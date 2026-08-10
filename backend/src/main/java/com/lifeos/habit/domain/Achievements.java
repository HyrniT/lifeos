package com.lifeos.habit.domain;

import java.util.List;
import java.util.function.Predicate;

/**
 * The achievement catalogue.
 *
 * Definitions live in code rather than the database: they are product content that
 * changes with releases, and keeping them here means the unlock rules are reviewed
 * alongside the logic that evaluates them.
 */
public final class Achievements {

    private Achievements() {
    }

    /** Snapshot of the numbers an unlock rule is allowed to look at. */
    public record Progress(
            long totalCheckIns,
            int currentDayStreak,
            int longestDayStreak,
            int bestHabitStreak,
            int habitCount,
            int level,
            int checkInsToday,
            int earlyCheckIns,
            int perfectWeeks
    ) {
    }

    public record Definition(
            String code,
            String title,
            String description,
            String icon,
            String tier,
            int goal,
            Predicate<Progress> unlocked,
            java.util.function.ToDoubleFunction<Progress> progressFn
    ) {
    }

    public static final List<Definition> ALL = List.of(
            def("FIRST_STEP", "First Step", "Complete your very first check-in", "footprints", "BRONZE", 1,
                    p -> p.totalCheckIns() >= 1, p -> ratio(p.totalCheckIns(), 1)),

            def("GETTING_STARTED", "Getting Started", "Log 10 check-ins", "sprout", "BRONZE", 10,
                    p -> p.totalCheckIns() >= 10, p -> ratio(p.totalCheckIns(), 10)),

            def("CENTURION", "Centurion", "Log 100 check-ins", "shield", "SILVER", 100,
                    p -> p.totalCheckIns() >= 100, p -> ratio(p.totalCheckIns(), 100)),

            def("MILLENNIAL", "Thousand Marks", "Log 1000 check-ins", "crown", "GOLD", 1000,
                    p -> p.totalCheckIns() >= 1000, p -> ratio(p.totalCheckIns(), 1000)),

            def("WEEK_WARRIOR", "Week Warrior", "Keep a 7-day streak", "flame", "BRONZE", 7,
                    p -> p.longestDayStreak() >= 7, p -> ratio(p.longestDayStreak(), 7)),

            def("FORTNIGHT", "Fortnight", "Keep a 14-day streak", "flame", "SILVER", 14,
                    p -> p.longestDayStreak() >= 14, p -> ratio(p.longestDayStreak(), 14)),

            def("MONTH_MASTER", "Month Master", "Keep a 30-day streak", "calendar-check", "SILVER", 30,
                    p -> p.longestDayStreak() >= 30, p -> ratio(p.longestDayStreak(), 30)),

            def("QUARTER_LEGEND", "Quarter Legend", "Keep a 90-day streak", "trophy", "GOLD", 90,
                    p -> p.longestDayStreak() >= 90, p -> ratio(p.longestDayStreak(), 90)),

            def("YEAR_OF_YOU", "Year of You", "Keep a 365-day streak", "gem", "PLATINUM", 365,
                    p -> p.longestDayStreak() >= 365, p -> ratio(p.longestDayStreak(), 365)),

            def("HABIT_ARCHITECT", "Habit Architect", "Track 5 habits at once", "layers", "BRONZE", 5,
                    p -> p.habitCount() >= 5, p -> ratio(p.habitCount(), 5)),

            def("PORTFOLIO", "Full Portfolio", "Track 12 habits at once", "grid", "GOLD", 12,
                    p -> p.habitCount() >= 12, p -> ratio(p.habitCount(), 12)),

            def("LEVEL_5", "Apprentice", "Reach level 5", "star", "BRONZE", 5,
                    p -> p.level() >= 5, p -> ratio(p.level(), 5)),

            def("LEVEL_10", "Journeyman", "Reach level 10", "star", "SILVER", 10,
                    p -> p.level() >= 10, p -> ratio(p.level(), 10)),

            def("LEVEL_25", "Master", "Reach level 25", "award", "GOLD", 25,
                    p -> p.level() >= 25, p -> ratio(p.level(), 25)),

            def("PERFECT_DAY", "Perfect Day", "Finish every habit due today", "check-circle", "SILVER", 1,
                    p -> p.checkInsToday() > 0 && p.checkInsToday() >= p.habitCount() && p.habitCount() > 0,
                    p -> p.habitCount() == 0 ? 0 : ratio(p.checkInsToday(), p.habitCount())),

            def("PERFECT_WEEK", "Perfect Week", "Finish every habit for a full week", "sparkles", "GOLD", 1,
                    p -> p.perfectWeeks() >= 1, p -> ratio(p.perfectWeeks(), 1)),

            def("EARLY_BIRD", "Early Bird", "Check in before 07:00 twenty times", "sunrise", "SILVER", 20,
                    p -> p.earlyCheckIns() >= 20, p -> ratio(p.earlyCheckIns(), 20)),

            def("IRON_WILL", "Iron Will", "Push one habit to a 100-day streak", "anvil", "PLATINUM", 100,
                    p -> p.bestHabitStreak() >= 100, p -> ratio(p.bestHabitStreak(), 100))
    );

    private static Definition def(String code, String title, String description, String icon, String tier,
                                  int goal, Predicate<Progress> unlocked,
                                  java.util.function.ToDoubleFunction<Progress> progressFn) {
        return new Definition(code, title, description, icon, tier, goal, unlocked, progressFn);
    }

    private static double ratio(long value, long goal) {
        if (goal <= 0) {
            return 1.0;
        }
        return Math.min(1.0, (double) value / goal);
    }
}
