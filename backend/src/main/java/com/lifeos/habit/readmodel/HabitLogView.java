package com.lifeos.habit.readmodel;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One completed check-in. The heatmap and streak maths both read from this table. */
@Entity
@Table(name = "habit_log", schema = "habit", uniqueConstraints = {
        // One entry per habit per day — a second check-in updates the value instead.
        @UniqueConstraint(name = "uk_log_habit_date", columnNames = {"habit_id", "log_date"})
}, indexes = {
        @Index(name = "idx_log_user_date", columnList = "user_id,log_date"),
        @Index(name = "idx_log_habit_date", columnList = "habit_id,log_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HabitLogView {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "habit_id", nullable = false)
    private UUID habitId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(nullable = false)
    @Builder.Default
    private double value = 1.0;

    @Column(length = 500)
    private String note;

    /** 1 (rough) … 5 (great); optional, powers the mood-vs-consistency chart. */
    @Column
    private Integer mood;

    @Column(name = "xp_awarded", nullable = false)
    @Builder.Default
    private int xpAwarded = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
