package com.lifeos.habit.domain;

/** The vocabulary of the habit domain, kept in one place for the API docs to mirror. */
public final class HabitEnums {

    private HabitEnums() {
    }

    /** Whether the user is building something up or cutting something out. */
    public enum HabitType {
        BUILD,
        QUIT
    }

    public enum Frequency {
        /** Every day. */
        DAILY,
        /** N times within a calendar week, any days. */
        WEEKLY_TARGET,
        /** Only on the selected weekdays. */
        SPECIFIC_DAYS,
        /** Every N days from the last completion. */
        INTERVAL,
        /** N times within a calendar month. */
        MONTHLY_TARGET
    }

    public enum Unit {
        TIMES, MINUTES, HOURS, PAGES, STEPS, KILOMETRES, MILLILITRES, GRAMS, CUSTOM
    }

    /**
     * Difficulty drives the XP award. The curve is deliberately gentle — a 4x spread
     * between trivial and epic keeps the easy habits worth doing.
     */
    public enum Difficulty {
        TRIVIAL(5),
        EASY(10),
        MEDIUM(20),
        HARD(35),
        EPIC(60);

        private final int baseXp;

        Difficulty(int baseXp) {
            this.baseXp = baseXp;
        }

        public int baseXp() {
            return baseXp;
        }
    }
}
