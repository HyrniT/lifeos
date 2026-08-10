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

/**
 * The RPG layer: experience, level, coins and the cross-habit day streak.
 *
 * Levelling uses a quadratic curve — {@code xpForLevel(n) = 50 * n * (n + 1)} — so
 * early levels arrive quickly and later ones need real consistency.
 */
@Entity
@Table(name = "user_stats", schema = "habit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStats {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    @Builder.Default
    private long xp = 0;

    @Column(nullable = false)
    @Builder.Default
    private int level = 1;

    @Column(nullable = false)
    @Builder.Default
    private long coins = 0;

    /** Drops when a habit is missed; a visible cost keeps streaks meaningful. */
    @Column(nullable = false)
    @Builder.Default
    private int hp = 100;

    @Column(name = "total_check_ins", nullable = false)
    @Builder.Default
    private long totalCheckIns = 0;

    @Column(name = "current_day_streak", nullable = false)
    @Builder.Default
    private int currentDayStreak = 0;

    @Column(name = "longest_day_streak", nullable = false)
    @Builder.Default
    private int longestDayStreak = 0;

    @Column(name = "last_active_date")
    private LocalDate lastActiveDate;

    /** Lets a user miss one day without losing the streak; earned every 14 days. */
    @Column(name = "streak_freezes", nullable = false)
    @Builder.Default
    private int streakFreezes = 1;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static long xpRequiredForLevel(int level) {
        return 50L * level * (level + 1);
    }

    /** Recomputes {@link #level} from {@link #xp}. Returns true when a level was gained. */
    public boolean recalculateLevel() {
        int newLevel = 1;
        while (xp >= xpRequiredForLevel(newLevel)) {
            newLevel++;
        }
        boolean levelledUp = newLevel > this.level;
        this.level = newLevel;
        return levelledUp;
    }

    public long xpIntoCurrentLevel() {
        return xp - (level > 1 ? xpRequiredForLevel(level - 1) : 0);
    }

    public long xpNeededForNextLevel() {
        return xpRequiredForLevel(level) - (level > 1 ? xpRequiredForLevel(level - 1) : 0);
    }
}
