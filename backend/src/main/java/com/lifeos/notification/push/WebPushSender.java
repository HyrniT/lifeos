package com.lifeos.notification.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeos.notification.domain.PushSubscription;
import com.lifeos.notification.domain.StoredNotification;
import com.lifeos.notification.repo.PushSubscriptionRepository;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Delivers a notification to every browser a user has subscribed, using the Web
 * Push protocol (RFC 8291) — the one channel that works when the app is closed.
 *
 * Failure handling is the part that matters operationally. A push endpoint that
 * answers 404 or 410 is permanently gone (browser reinstalled, permission
 * revoked), so the subscription is deleted immediately; anything else is treated
 * as transient and only removed after repeated failures. Without that, dead
 * endpoints accumulate forever and every send gets slower.
 */
@Service
@ConditionalOnProperty(name = "lifeos.push.enabled", havingValue = "true", matchIfMissing = true)
public class WebPushSender {

    private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);
    private static final int MAX_FAILURES = 5;

    private final PushSubscriptionRepository subscriptions;
    private final ObjectMapper mapper;
    private final VapidKeys vapid;
    private final int ttlSeconds;
    private PushService pushService;

    public WebPushSender(PushSubscriptionRepository subscriptions, ObjectMapper mapper,
                         VapidKeys vapid,
                         @Value("${lifeos.push.ttl-seconds:86400}") int ttlSeconds) {
        this.subscriptions = subscriptions;
        this.mapper = mapper;
        this.vapid = vapid;
        this.ttlSeconds = ttlSeconds;

        try {
            this.pushService = new PushService(vapid.publicKey(), vapid.privateKey(), vapid.subject());
        } catch (Exception ex) {
            // Never fail startup over this: in-app delivery must keep working.
            log.error("Web Push could not be initialised; push delivery is disabled", ex);
            this.pushService = null;
        }
    }

    public boolean isAvailable() {
        return pushService != null;
    }

    @Transactional
    public int send(UUID userId, StoredNotification notification) {
        if (pushService == null) {
            return 0;
        }
        List<PushSubscription> targets = subscriptions.findByUserId(userId);
        if (targets.isEmpty()) {
            return 0;
        }

        byte[] payload = buildPayload(notification);
        int delivered = 0;

        for (PushSubscription subscription : targets) {
            try {
                Notification push = new Notification(
                        subscription.getEndpoint(),
                        subscription.getP256dh(),
                        subscription.getAuth(),
                        payload,
                        ttlSeconds);

                HttpResponse response = pushService.send(push);
                int status = response.getStatusLine().getStatusCode();

                if (status == 404 || status == 410) {
                    log.debug("Push endpoint gone ({}), removing subscription {}", status, subscription.getId());
                    subscriptions.delete(subscription);
                } else if (status >= 200 && status < 300) {
                    subscription.setLastSuccessAt(Instant.now());
                    subscription.setFailureCount(0);
                    subscriptions.save(subscription);
                    delivered++;
                } else {
                    recordFailure(subscription, "HTTP " + status);
                }
            } catch (Exception ex) {
                recordFailure(subscription, ex.getMessage());
            }
        }
        return delivered;
    }

    private void recordFailure(PushSubscription subscription, String reason) {
        subscription.setFailureCount(subscription.getFailureCount() + 1);
        if (subscription.getFailureCount() >= MAX_FAILURES) {
            log.info("Dropping push subscription {} after {} failures ({})",
                    subscription.getId(), subscription.getFailureCount(), reason);
            subscriptions.delete(subscription);
        } else {
            log.debug("Push to {} failed ({}), attempt {}",
                    subscription.getId(), reason, subscription.getFailureCount());
            subscriptions.save(subscription);
        }
    }

    /**
     * The payload the service worker receives. Kept small on purpose — push
     * services cap the encrypted body at about 4 KB.
     */
    private byte[] buildPayload(StoredNotification notification) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", notification.getId().toString());
        body.put("kind", notification.getKind());
        body.put("title", notification.getTitle());
        body.put("body", notification.getBody());
        body.put("icon", notification.getIcon());
        body.put("severity", notification.getSeverity());
        body.put("deepLink", notification.getDeepLink());
        try {
            return mapper.writeValueAsBytes(body);
        } catch (Exception ex) {
            return ("{\"title\":\"" + notification.getTitle() + "\"}").getBytes(StandardCharsets.UTF_8);
        }
    }
}
