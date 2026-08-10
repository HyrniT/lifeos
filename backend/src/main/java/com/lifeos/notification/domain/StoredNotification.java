package com.lifeos.notification.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The inbox, and the idempotency guard.
 *
 * {@code dedupe_key} is uniquely indexed. Schedulers fire on an interval, across
 * replicas, and replay their window after a restart, so the same reminder is
 * produced repeatedly by design; the insert simply fails the second time and the
 * duplicate is dropped. That is a stronger guarantee than a "sent" flag on the
 * source row, and it is what lets a task carry several reminders at different
 * lead times without any per-task bookkeeping.
 */
@Entity
@Table(name = "notification", schema = "notification", indexes = {
        @Index(name = "idx_notification_user", columnList = "user_id,created_at"),
        @Index(name = "idx_notification_pending", columnList = "delivered,deliver_after")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_notification_dedupe", columnNames = "dedupe_key")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoredNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 48)
    private String kind;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(length = 500)
    private String body;

    @Column(length = 48)
    @Builder.Default
    private String icon = "bell";

    @Column(nullable = false, length = 16)
    @Builder.Default
    private String severity = "info";

    @Column(name = "deep_link", length = 256)
    private String deepLink;

    @Column(name = "dedupe_key", nullable = false, length = 200)
    private String dedupeKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> data;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean read = false;

    /**
     * Held back until this instant — how quiet hours are honoured without losing
     * the notification.
     */
    @Column(name = "deliver_after")
    private Instant deliverAfter;

    /** Whether it has been pushed out (SSE / Web Push) yet. */
    @Column(nullable = false)
    @Builder.Default
    private boolean delivered = false;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
