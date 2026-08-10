package com.lifeos.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A local projection of the bits of the user profile this service needs, kept up
 * to date from {@code lifeos.user.events}.
 *
 * The alternative — calling auth-service whenever a reminder is scheduled — would
 * put a synchronous dependency on the hot path of a background job, and take the
 * scheduler down with it whenever auth-service restarts.
 */
/*
 * Three packages project the user's timezone into their own table, and all three
 * classes are called UserSettings. Distinct schemas keep the tables apart; the
 * explicit entity name keeps Hibernate's registry apart, which is keyed on the
 * simple class name and would otherwise reject the second one at startup.
 */
@Entity(name = "NotificationUserSettings")
@Table(name = "user_settings", schema = "notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSettings {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(length = 255)
    private String email;

    @Column(name = "display_name", length = 120)
    private String displayName;

    @Column(nullable = false, length = 64)
    @Builder.Default
    private String timezone = "UTC";

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
