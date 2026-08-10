package com.lifeos.habit.readmodel;

import com.lifeos.habit.domain.HabitEnums.Difficulty;
import com.lifeos.habit.domain.HabitEnums.Frequency;
import com.lifeos.habit.domain.HabitEnums.HabitType;
import com.lifeos.habit.domain.HabitEnums.Unit;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The query-side projection of a habit — everything a list or detail screen needs
 * in one row, including the derived streak numbers so no screen has to aggregate.
 *
 * Safe to truncate and rebuild from {@code event_store}.
 */
@Entity
@Table(name = "habit_view", schema = "habit", indexes = {
        @Index(name = "idx_habit_user", columnList = "user_id,archived,sort_order"),
        @Index(name = "idx_habit_user_created", columnList = "user_id,created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HabitView {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 48)
    @Builder.Default
    private String icon = "target";

    /** Hex colour used as the card accent; the UI is monochrome, so this drives tone. */
    @Column(length = 16)
    @Builder.Default
    private String color = "#111111";

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private HabitType type = HabitType.BUILD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    @Builder.Default
    private Frequency frequency = Frequency.DAILY;

    /** Populated for SPECIFIC_DAYS: 1 = Monday … 7 = Sunday. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "habit_view_days", schema = "habit", joinColumns = @JoinColumn(name = "habit_id"))
    @Column(name = "day_of_week")
    @Builder.Default
    private Set<Integer> daysOfWeek = new LinkedHashSet<>();

    @Column(name = "interval_days")
    @Builder.Default
    private Integer intervalDays = 1;

    @Column(name = "target_per_period", nullable = false)
    @Builder.Default
    private int targetPerPeriod = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private Unit unit = Unit.TIMES;

    @Column(name = "unit_label", length = 24)
    private String unitLabel;

    @Column(name = "target_value")
    @Builder.Default
    private Double targetValue = 1.0;

    @Column(name = "reminder_time")
    private LocalTime reminderTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private Difficulty difficulty = Difficulty.MEDIUM;

    @Column(length = 48)
    @Builder.Default
    private String category = "general";

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean archived = false;

    // ---- derived, maintained by the projector ---------------------------
    @Column(name = "current_streak", nullable = false)
    @Builder.Default
    private int currentStreak = 0;

    @Column(name = "longest_streak", nullable = false)
    @Builder.Default
    private int longestStreak = 0;

    @Column(name = "total_check_ins", nullable = false)
    @Builder.Default
    private long totalCheckIns = 0;

    @Column(name = "last_check_in_date")
    private LocalDate lastCheckInDate;

    @Column(name = "completion_rate_30d")
    @Builder.Default
    private Double completionRate30d = 0.0;

    /** Mirrors the aggregate version so clients can send it back for optimistic updates. */
    @Column(name = "version_no", nullable = false)
    @Builder.Default
    private long versionNo = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
