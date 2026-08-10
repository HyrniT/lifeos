package com.lifeos.notification.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * What a user wants to be told about, how, and when.
 *
 * Defaults are chosen so a brand-new account is useful without visiting settings,
 * but never noisy: reminders on, quiet hours overnight, and the digest off until
 * asked for.
 */
@Entity
@Table(name = "notification_preference", schema = "notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreference {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    // ---- channels --------------------------------------------------------
    @Column(name = "in_app_enabled", nullable = false)
    @Builder.Default
    private boolean inAppEnabled = true;

    @Column(name = "push_enabled", nullable = false)
    @Builder.Default
    private boolean pushEnabled = true;

    @Column(name = "email_enabled", nullable = false)
    @Builder.Default
    private boolean emailEnabled = false;

    // ---- which kinds -----------------------------------------------------
    /** Kinds the user has switched off; everything not listed is on. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "notification_muted_kind", schema = "notification", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "kind", length = 48, nullable = false)
    @Builder.Default
    private Set<String> mutedKinds = new LinkedHashSet<>();

    // ---- deadline lead times ---------------------------------------------
    /**
     * How long before a task's due time to warn, in minutes. Multiple entries mean
     * multiple reminders; an empty set means "only at the deadline itself".
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "notification_lead_time", schema = "notification", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "minutes_before", nullable = false)
    @Builder.Default
    private Set<Integer> leadTimeMinutes = new LinkedHashSet<>(Set.of(1440, 120));

    @Column(name = "remind_at_deadline", nullable = false)
    @Builder.Default
    private boolean remindAtDeadline = true;

    @Column(name = "remind_when_overdue", nullable = false)
    @Builder.Default
    private boolean remindWhenOverdue = true;

    // ---- daily summary ----------------------------------------------------
    @Column(name = "daily_summary_enabled", nullable = false)
    @Builder.Default
    private boolean dailySummaryEnabled = true;

    @Column(name = "daily_summary_time", nullable = false)
    @Builder.Default
    private LocalTime dailySummaryTime = LocalTime.of(8, 0);

    // ---- quiet hours ------------------------------------------------------
    @Column(name = "quiet_hours_enabled", nullable = false)
    @Builder.Default
    private boolean quietHoursEnabled = true;

    @Column(name = "quiet_from", nullable = false)
    @Builder.Default
    private LocalTime quietFrom = LocalTime.of(22, 0);

    @Column(name = "quiet_to", nullable = false)
    @Builder.Default
    private LocalTime quietTo = LocalTime.of(7, 0);

    /** IANA zone; mirrored from the user profile so quiet hours mean local hours. */
    @Column(nullable = false, length = 64)
    @Builder.Default
    private String timezone = "UTC";

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    public static NotificationPreference defaultsFor(UUID userId, String timezone) {
        return NotificationPreference.builder()
                .userId(userId)
                .timezone(timezone == null || timezone.isBlank() ? "UTC" : timezone)
                .build();
    }

    public boolean allows(String kind) {
        return mutedKinds == null || !mutedKinds.contains(kind);
    }

    /**
     * Quiet hours normally wrap midnight (22:00 → 07:00), so the comparison has to
     * handle the range being "outside" rather than "between".
     */
    public boolean isQuiet(LocalTime localTime) {
        if (!quietHoursEnabled) {
            return false;
        }
        if (quietFrom.equals(quietTo)) {
            return false;
        }
        if (quietFrom.isBefore(quietTo)) {
            return !localTime.isBefore(quietFrom) && localTime.isBefore(quietTo);
        }
        return !localTime.isBefore(quietFrom) || localTime.isBefore(quietTo);
    }
}
