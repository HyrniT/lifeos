package com.lifeos.habit.dto;

import com.lifeos.habit.domain.HabitEnums.Difficulty;
import com.lifeos.habit.domain.HabitEnums.Frequency;
import com.lifeos.habit.domain.HabitEnums.HabitType;
import com.lifeos.habit.domain.HabitEnums.Unit;
import com.lifeos.habit.readmodel.HabitLogView;
import com.lifeos.habit.readmodel.HabitView;
import com.lifeos.habit.readmodel.UserStats;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class HabitDtos {

    private HabitDtos() {
    }

    // ------------------------------------------------------------ commands
    public record CreateHabitRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 48) String icon,
            @Size(max = 16) String color,
            @Size(max = 500) String description,
            HabitType type,
            Frequency frequency,
            Set<Integer> daysOfWeek,
            @Min(1) @Max(365) Integer intervalDays,
            @Min(1) @Max(100) Integer targetPerPeriod,
            Unit unit,
            @Size(max = 24) String unitLabel,
            Double targetValue,
            LocalTime reminderTime,
            Difficulty difficulty,
            @Size(max = 48) String category
    ) {
    }

    /** Every field optional — only what is present gets written as a change event. */
    public record UpdateHabitRequest(
            @Size(max = 120) String name,
            @Size(max = 48) String icon,
            @Size(max = 16) String color,
            @Size(max = 500) String description,
            HabitType type,
            Frequency frequency,
            Set<Integer> daysOfWeek,
            Integer intervalDays,
            Integer targetPerPeriod,
            Unit unit,
            String unitLabel,
            Double targetValue,
            LocalTime reminderTime,
            Difficulty difficulty,
            String category,
            Integer sortOrder
    ) {
    }

    public record CheckInRequest(
            LocalDate date,
            Double value,
            @Size(max = 500) String note,
            @Min(1) @Max(5) Integer mood
    ) {
    }

    public record ReorderRequest(List<UUID> orderedIds) {
    }

    // ----------------------------------------------------------- responses
    public record HabitResponse(
            UUID id,
            String name,
            String icon,
            String color,
            String description,
            HabitType type,
            Frequency frequency,
            Set<Integer> daysOfWeek,
            Integer intervalDays,
            int targetPerPeriod,
            Unit unit,
            String unitLabel,
            Double targetValue,
            LocalTime reminderTime,
            Difficulty difficulty,
            String category,
            int sortOrder,
            boolean archived,
            int currentStreak,
            int longestStreak,
            long totalCheckIns,
            LocalDate lastCheckInDate,
            Double completionRate30d,
            boolean doneToday,
            long version,
            Instant createdAt
    ) {
        public static HabitResponse from(HabitView h, boolean doneToday) {
            return new HabitResponse(h.getId(), h.getName(), h.getIcon(), h.getColor(), h.getDescription(),
                    h.getType(), h.getFrequency(), h.getDaysOfWeek(), h.getIntervalDays(),
                    h.getTargetPerPeriod(), h.getUnit(), h.getUnitLabel(), h.getTargetValue(),
                    h.getReminderTime(), h.getDifficulty(), h.getCategory(), h.getSortOrder(),
                    h.isArchived(), h.getCurrentStreak(), h.getLongestStreak(), h.getTotalCheckIns(),
                    h.getLastCheckInDate(), h.getCompletionRate30d(), doneToday,
                    h.getVersionNo(), h.getCreatedAt());
        }
    }

    public record CheckInResponse(
            UUID habitId,
            LocalDate date,
            double value,
            int xpAwarded,
            int currentStreak,
            boolean milestoneReached,
            StatsResponse stats,
            List<String> newAchievements
    ) {
    }

    public record LogEntry(
            UUID id,
            UUID habitId,
            LocalDate date,
            double value,
            String note,
            Integer mood,
            int xpAwarded
    ) {
        public static LogEntry from(HabitLogView l) {
            return new LogEntry(l.getId(), l.getHabitId(), l.getLogDate(), l.getValue(),
                    l.getNote(), l.getMood(), l.getXpAwarded());
        }
    }

    public record StatsResponse(
            long xp,
            int level,
            long coins,
            int hp,
            long xpIntoLevel,
            long xpForNextLevel,
            double levelProgress,
            long totalCheckIns,
            int currentDayStreak,
            int longestDayStreak,
            int streakFreezes,
            LocalDate lastActiveDate
    ) {
        public static StatsResponse from(UserStats s) {
            long into = s.xpIntoCurrentLevel();
            long need = s.xpNeededForNextLevel();
            return new StatsResponse(s.getXp(), s.getLevel(), s.getCoins(), s.getHp(), into, need,
                    need == 0 ? 0 : Math.min(1.0, (double) into / need),
                    s.getTotalCheckIns(), s.getCurrentDayStreak(), s.getLongestDayStreak(),
                    s.getStreakFreezes(), s.getLastActiveDate());
        }
    }

    public record AchievementView(
            String code,
            String title,
            String description,
            String icon,
            String tier,
            boolean unlocked,
            Instant unlockedAt,
            double progress
    ) {
    }

    /** One cell of the GitHub-style contribution heatmap. */
    public record HeatmapCell(LocalDate date, int count, double intensity) {
    }

    public record HabitInsights(
            UUID habitId,
            String habitName,
            int currentStreak,
            int longestStreak,
            long totalCheckIns,
            double completionRate7d,
            double completionRate30d,
            double completionRate90d,
            Map<String, Double> weekdayCompletion,
            List<HeatmapCell> heatmap,
            String bestDay,
            String worstDay,
            Double averageMood,
            String trend
    ) {
    }

    public record TodaySummary(
            LocalDate date,
            int totalDue,
            int completed,
            double completionRate,
            int xpEarnedToday,
            List<HabitResponse> due,
            StatsResponse stats
    ) {
    }
}
