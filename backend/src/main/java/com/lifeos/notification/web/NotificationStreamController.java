package com.lifeos.notification.web;

import com.lifeos.common.security.UserPrincipal;
import com.lifeos.notification.domain.StoredNotification;
import com.lifeos.notification.web.dto.NotificationView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Live feed for an open tab.
 *
 * SSE rather than WebSockets: the traffic is one-directional, it survives proxies
 * that mangle upgrades, and the browser reconnects on its own.
 *
 * The emitter map is per-instance, which is correct — a browser holds one
 * connection to one replica. Anything missed while disconnected is still in the
 * durable inbox, and Web Push covers the case where no tab is open at all.
 */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
public class NotificationStreamController {

    private static final Logger log = LoggerFactory.getLogger(NotificationStreamController.class);
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Live notification stream (SSE)")
    public SseEmitter stream(@AuthenticationPrincipal UserPrincipal me) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(me.id(), k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(me.id(), emitter));
        emitter.onTimeout(() -> remove(me.id(), emitter));
        emitter.onError(e -> remove(me.id(), emitter));

        try {
            // An immediate event makes the client's "connected" state honest rather
            // than optimistic, and defeats proxies that buffer until the first byte.
            emitter.send(SseEmitter.event().name("connected")
                    .data(Map.of("ok", true, "userId", me.id().toString())));
        } catch (IOException ex) {
            remove(me.id(), emitter);
        }
        return emitter;
    }

    public void push(StoredNotification notification) {
        List<SseEmitter> targets = emitters.get(notification.getUserId());
        if (targets == null || targets.isEmpty()) {
            return;
        }
        NotificationView payload = NotificationView.from(notification);
        for (SseEmitter emitter : targets) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(payload));
            } catch (Exception ex) {
                log.debug("Dropping dead SSE connection for {}", notification.getUserId());
                remove(notification.getUserId(), emitter);
            }
        }
    }

    public int connectedClients() {
        return emitters.values().stream().mapToInt(List::size).sum();
    }

    private void remove(UUID userId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(userId);
            }
        }
    }
}
