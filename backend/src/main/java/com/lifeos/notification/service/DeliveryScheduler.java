package com.lifeos.notification.service;

import com.lifeos.notification.domain.StoredNotification;
import com.lifeos.notification.repo.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Releases notifications that were held back by quiet hours, and prunes old ones.
 *
 * Runs every minute: quiet hours end on a wall-clock minute, and a coarser tick
 * would mean the 07:00 batch arrives at 07:05 for everyone.
 */
@Component
public class DeliveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeliveryScheduler.class);

    private final NotificationRepository notifications;
    private final NotificationService notificationService;
    private final PreferenceService preferences;

    public DeliveryScheduler(NotificationRepository notifications,
                             NotificationService notificationService,
                             PreferenceService preferences) {
        this.notifications = notifications;
        this.notificationService = notificationService;
        this.preferences = preferences;
    }

    @Scheduled(fixedDelayString = "${lifeos.notifications.dispatch-ms:60000}")
    @Transactional
    public void releaseDeferred() {
        List<StoredNotification> pending =
                notifications.pendingDelivery(Instant.now(), PageRequest.of(0, 200));
        if (pending.isEmpty()) {
            return;
        }
        for (StoredNotification notification : pending) {
            try {
                notificationService.deliver(notification, preferences.forUser(notification.getUserId()));
            } catch (Exception ex) {
                // One bad row must not stall the queue for everyone else.
                log.warn("Could not deliver notification {}: {}", notification.getId(), ex.getMessage());
                notification.setDelivered(true);
                notifications.save(notification);
            }
        }
        log.debug("Released {} deferred notification(s)", pending.size());
    }

    @Scheduled(cron = "${lifeos.notifications.cleanup-cron:0 45 3 * * *}")
    @Transactional
    public void prune() {
        int removed = notifications.deleteOlderThan(Instant.now().minus(Duration.ofDays(90)));
        if (removed > 0) {
            log.info("Pruned {} notification(s) older than 90 days", removed);
        }
    }
}
