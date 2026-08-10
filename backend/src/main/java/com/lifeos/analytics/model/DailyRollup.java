package com.lifeos.analytics.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One row per user per day, holding every domain's numbers side by side.
 *
 * This is the shape the cross-domain charts want, and it is why the projector
 * exists at all: the join across habits, money and planning happens once, on the
 * way in, rather than across three query paths every time a chart is drawn.
 */
@Entity
@Table(name = "daily_rollup", schema = "analytics",
        uniqueConstraints = @UniqueConstraint(name = "uk_rollup_user_date",
                columnNames = {"user_id", "rollup_date"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyRollup {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Not "date": that is a reserved word in SQL and quoting it everywhere is worse. */
    @Column(name = "rollup_date", nullable = false)
    private LocalDate date;

    // ---- habits ----
    @Column(name = "habit_check_ins", nullable = false)
    @Builder.Default
    private int habitCheckIns = 0;

    @Column(name = "habits_due", nullable = false)
    @Builder.Default
    private int habitsDue = 0;

    @Column(name = "xp_earned", nullable = false)
    @Builder.Default
    private int xpEarned = 0;

    @Column(name = "best_streak", nullable = false)
    @Builder.Default
    private int bestStreak = 0;

    // ---- money ----
    @Column(name = "expense_total", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal expenseTotal = BigDecimal.ZERO;

    @Column(name = "income_total", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal incomeTotal = BigDecimal.ZERO;

    @Column(name = "transaction_count", nullable = false)
    @Builder.Default
    private int transactionCount = 0;

    /**
     * The one part of the document shape that had to be unnested.
     *
     * Eager because the caller is always the projector mutating it or a chart
     * summing it, and both would otherwise hit a lazy-load outside the session —
     * {@code open-in-view} is off.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "daily_rollup_category", schema = "analytics",
            joinColumns = @JoinColumn(name = "rollup_id"))
    @MapKeyColumn(name = "category_id", length = 64)
    @Column(name = "amount", precision = 19, scale = 4)
    @Builder.Default
    private Map<String, BigDecimal> spendByCategory = new HashMap<>();

    // ---- planning ----
    @Column(name = "tasks_completed", nullable = false)
    @Builder.Default
    private int tasksCompleted = 0;

    @Column(name = "tasks_created", nullable = false)
    @Builder.Default
    private int tasksCreated = 0;

    @Column(name = "focus_minutes", nullable = false)
    @Builder.Default
    private int focusMinutes = 0;

    @Column(name = "focus_sessions", nullable = false)
    @Builder.Default
    private int focusSessions = 0;

    // ---- wellbeing ----
    @Column(name = "mood")
    private Integer mood;

    @Column(name = "energy")
    private Integer energy;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    /** Composite id keeps the upsert cheap and the uniqueness obvious. */
    public static String idFor(UUID userId, LocalDate date) {
        return userId + ":" + date;
    }
}
