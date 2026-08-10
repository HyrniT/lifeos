package com.lifeos.notification.web.dto;

import com.lifeos.notification.domain.StoredNotification;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** The shape the web client and the service worker both consume. */
public record NotificationView(
        UUID id,
        UUID userId,
        String kind,
        String title,
        String body,
        String icon,
        String severity,
        String deepLink,
        Map<String, Object> data,
        boolean read,
        Instant createdAt
) {
    public static NotificationView from(StoredNotification n) {
        return new NotificationView(n.getId(), n.getUserId(), n.getKind(), n.getTitle(),
                n.getBody(), n.getIcon(), n.getSeverity(), n.getDeepLink(),
                n.getData() == null ? Map.of() : n.getData(), n.isRead(), n.getCreatedAt());
    }
}
