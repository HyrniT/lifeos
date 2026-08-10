package com.lifeos.notification.service;

import com.lifeos.common.event.ReminderMessage;
import com.lifeos.notification.domain.NotificationPreference;
import com.lifeos.notification.domain.StoredNotification;
import com.lifeos.notification.push.WebPushSender;
import com.lifeos.notification.repo.NotificationRepository;
import com.lifeos.notification.web.NotificationStreamController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.UUID;

/**
 * The single funnel every notification goes through, whatever produced it.
 *
 * Order matters and each step exists for a reason:
 *   1. muted kind      — the user said no; drop it before doing any work
 *   2. dedupe          — schedulers replay, so this is the norm, not the exception
 *   3. quiet hours     — defer rather than drop, so nothing is silently lost
 *   4. persist         — durable inbox, survives a restart and a Redis flush
 *   5. fan out         — SSE for an open tab, Web Push for a closed one
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** Kinds that ignore quiet hours: the user explicitly asked to be interrupted. */
    private static final List<String> ALWAYS_DELIVER =
            List.of(ReminderMessage.Kind.SECURITY, ReminderMessage.Kind.TEST);

    private final NotificationRepository notifications;
    private final PreferenceService preferences;
    private final NotificationStreamController stream;
    private final WebPushSender push;

    public NotificationService(NotificationRepository notifications,
                               PreferenceService preferences,
                               NotificationStreamController stream,
                               @Autowired(required = false) WebPushSender push) {
        this.notifications = notifications;
        this.preferences = preferences;
        this.stream = stream;
        this.push = push;
    }

    /**
     * @return the stored notification, or empty when it was muted or is a duplicate.
     */
    @Transactional
    public java.util.Optional<StoredNotification> accept(ReminderMessage message) {
        if (message.userId() == null || message.dedupeKey() == null) {
            log.warn("Discarding reminder without a user or dedupe key: {}", message.kind());
            return java.util.Optional.empty();
        }

        NotificationPreference preference = preferences.forUser(message.userId());

        if (!preference.allows(message.kind())) {
            log.debug("Muted kind {} for {}", message.kind(), message.userId());
            return java.util.Optional.empty();
        }
        if (!wantsThisReminder(message, preference)) {
            return java.util.Optional.empty();
        }

        // Cheap pre-check; the unique index below is the actual guarantee, since two
        // replicas can pass this check simultaneously.
        if (notifications.existsByDedupeKey(message.dedupeKey())) {
            return java.util.Optional.empty();
        }

        Instant deliverAfter = resolveDeliveryTime(message, preference);

        StoredNotification stored = StoredNotification.builder()
                .userId(message.userId())
                .kind(message.kind())
                .title(truncate(message.title(), 160))
                .body(truncate(message.body(), 500))
                .icon(message.icon())
                .severity(message.severity())
                .deepLink(message.deepLink())
                .dedupeKey(message.dedupeKey())
                .data(message.data())
                .deliverAfter(deliverAfter)
                .createdAt(Instant.now())
                .build();

        try {
            stored = notifications.save(stored);
            notifications.flush();
        } catch (DataIntegrityViolationException ex) {
            // The other replica won the race. Exactly what the index is for.
            log.debug("Duplicate reminder {} dropped", message.dedupeKey());
            return java.util.Optional.empty();
        }

        if (deliverAfter == null || !deliverAfter.isAfter(Instant.now())) {
            deliver(stored, preference);
        } else {
            log.debug("Reminder {} deferred until {} (quiet hours)", message.dedupeKey(), deliverAfter);
        }
        return java.util.Optional.of(stored);
    }

    /**
     * Sends an already-stored notification out over every enabled channel.
     * Marks it delivered even when nothing was reachable — the inbox still has it,
     * and retrying forever would re-notify the moment a device comes back weeks later.
     */
    @Transactional
    public void deliver(StoredNotification stored, NotificationPreference preference) {
        if (preference.isInAppEnabled()) {
            stream.push(stored);
        }
        if (preference.isPushEnabled() && push != null && push.isAvailable()) {
            int sent = push.send(stored.getUserId(), stored);
            if (sent > 0) {
                log.debug("Pushed {} to {} device(s)", stored.getKind(), sent);
            }
        }
        stored.setDelivered(true);
        stored.setDeliveredAt(Instant.now());
        notifications.save(stored);
    }

    /**
     * The per-kind switches that need more than an on/off flag.
     *
     * Deadline reminders are the interesting case. The scheduling service emits a
     * candidate at every lead time the system supports and this decides which ones
     * the user actually asked for — which is what keeps preferences in one service
     * and off the scheduler's hot path.
     */
    private boolean wantsThisReminder(ReminderMessage message, NotificationPreference preference) {
        switch (message.kind()) {
            case ReminderMessage.Kind.TASK_DUE_SOON -> {
                Object lead = message.data() == null ? null : message.data().get("leadMinutes");
                if (lead == null) {
                    return true;
                }
                int minutes = ((Number) lead).intValue();
                boolean wanted = preference.getLeadTimeMinutes().contains(minutes);
                if (!wanted) {
                    log.trace("Lead time {}m not requested by {}", minutes, message.userId());
                }
                return wanted;
            }
            case ReminderMessage.Kind.TASK_DUE -> {
                return preference.isRemindAtDeadline();
            }
            case ReminderMessage.Kind.TASK_OVERDUE -> {
                return preference.isRemindWhenOverdue();
            }
            case ReminderMessage.Kind.DAILY_SUMMARY -> {
                return preference.isDailySummaryEnabled();
            }
            default -> {
                return true;
            }
        }
    }

    /**
     * Quiet hours push a notification to the end of the quiet window rather than
     * dropping it. A deadline reminder that arrives at 07:00 instead of 02:00 is
     * still useful; one that never arrives is a bug the user will blame the app for.
     */
    private Instant resolveDeliveryTime(ReminderMessage message, NotificationPreference preference) {
        Instant requested = message.notBefore();
        Instant base = requested == null || requested.isBefore(Instant.now()) ? Instant.now() : requested;

        if (message.urgent() || ALWAYS_DELIVER.contains(message.kind())) {
            return requested;
        }

        ZoneId zone = safeZone(preference.getTimezone());
        ZonedDateTime local = base.atZone(zone);

        if (!preference.isQuiet(local.toLocalTime())) {
            return requested;
        }

        // End of the quiet window, which is tomorrow's date when the window wraps midnight.
        LocalTime quietTo = preference.getQuietTo();
        ZonedDateTime release = local.with(quietTo);
        if (!release.isAfter(local)) {
            release = release.plusDays(1);
        }
        return release.toInstant();
    }

    // ---------------------------------------------------------------- inbox
    @Transactional(readOnly = true)
    public List<StoredNotification> inbox(UUID userId, boolean unreadOnly, int limit) {
        return notifications.inbox(userId, unreadOnly, Instant.now(),
                PageRequest.of(0, Math.min(limit, 200)));
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notifications.unreadCount(userId, Instant.now());
    }

    @Transactional
    public boolean markRead(UUID userId, UUID id) {
        return notifications.findByIdAndUserId(id, userId).map(n -> {
            n.setRead(true);
            notifications.save(n);
            return true;
        }).orElse(false);
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notifications.markAllRead(userId);
    }

    @Transactional
    public int clear(UUID userId) {
        return notifications.deleteAllForUser(userId);
    }

    private static ZoneId safeZone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (Exception ex) {
            return ZoneOffset.UTC;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
