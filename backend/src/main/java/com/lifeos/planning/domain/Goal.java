package com.lifeos.planning.domain;

import com.lifeos.planning.domain.PlanningEnums.GoalStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A measurable objective.
 *
 * Progress is a number rather than a percentage the user types: "read 24 books,
 * currently at 9" survives a change of target, while "38% done" does not.
 */
@Entity
@Table(name = "goal", schema = "planning", indexes = {
        @Index(name = "idx_goal_user", columnList = "user_id,status,target_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(length = 48)
    @Builder.Default
    private String category = "personal";

    @Column(length = 48)
    @Builder.Default
    private String icon = "flag";

    @Column(length = 16)
    @Builder.Default
    private String color = "#111111";

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "target_value", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal targetValue = BigDecimal.ONE;

    @Column(name = "current_value", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal currentValue = BigDecimal.ZERO;

    @Column(length = 32)
    @Builder.Default
    private String unit = "steps";

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private GoalStatus status = GoalStatus.ACTIVE;

    @Column(name = "achieved_at")
    private Instant achievedAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    public double progress() {
        if (targetValue == null || targetValue.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        double raw = currentValue.doubleValue() / targetValue.doubleValue();
        return Math.min(1.0, Math.max(0.0, Math.round(raw * 1000) / 1000.0));
    }

    /**
     * How far through the time window the goal is, so the UI can show whether
     * progress is ahead of or behind schedule rather than just how much is done.
     */
    public Double timeElapsed() {
        if (startDate == null || targetDate == null || !targetDate.isAfter(startDate)) {
            return null;
        }
        long total = java.time.temporal.ChronoUnit.DAYS.between(startDate, targetDate);
        long done = java.time.temporal.ChronoUnit.DAYS.between(startDate, LocalDate.now());
        return Math.min(1.0, Math.max(0.0, (double) done / total));
    }
}
