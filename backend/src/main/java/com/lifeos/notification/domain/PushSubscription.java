package com.lifeos.notification.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One browser (or installed PWA) that agreed to receive Web Push.
 *
 * A user typically has several — laptop, phone, work machine — and each must be
 * delivered to separately. Subscriptions expire on their own when a browser is
 * reinstalled or permission is revoked; the push endpoint then answers 404/410
 * and the sender deletes the row.
 */
@Entity
@Table(name = "push_subscription", schema = "notification", indexes = {
        @Index(name = "idx_push_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * The push service URL. Long by nature (FCM endpoints run to ~200 chars, others
     * more), and unique per browser, which makes it the natural identity.
     */
    @Column(nullable = false, unique = true, length = 1024)
    private String endpoint;

    /** Client public key (base64url), used to encrypt the payload. */
    @Column(name = "p256dh", nullable = false, length = 255)
    private String p256dh;

    /** Client auth secret (base64url). */
    @Column(nullable = false, length = 255)
    private String auth;

    @Column(name = "user_agent", length = 256)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "failure_count", nullable = false)
    @Builder.Default
    private int failureCount = 0;
}
