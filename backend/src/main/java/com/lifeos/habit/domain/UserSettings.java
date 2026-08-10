package com.lifeos.habit.domain;

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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * The user's timezone, projected from {@code lifeos.user.events}.
 *
 * A habit reminder is set as "07:00" and has to mean 07:00 where the user is.
 * Without this the schedule runs on the server's clock, which is UTC in every
 * deployment here — so a Vietnamese user's morning reminder would arrive at 14:00.
 */
/*
 * Three packages project the user's timezone into their own table, and all three
 * classes are called UserSettings. Distinct schemas keep the tables apart; the
 * explicit entity name keeps Hibernate's registry apart, which is keyed on the
 * simple class name and would otherwise reject the second one at startup.
 */
@Entity(name = "HabitUserSettings")
@Table(name = "user_settings", schema = "habit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSettings {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 64)
    @Builder.Default
    private String timezone = "UTC";

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    public ZoneId zone() {
        try {
            return ZoneId.of(timezone);
        } catch (Exception ex) {
            return ZoneOffset.UTC;
        }
    }
}
