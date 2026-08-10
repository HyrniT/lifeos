package com.lifeos.common.event;

/**
 * Every event stream and event type name in the system, in one place.
 *
 * These were Kafka topic names. Nothing subscribes over a network any more, but the
 * grouping survived the move because it is what a handler filters on — and keeping
 * the names means the event log written before the merge still reads correctly.
 */
public final class Topics {

    private Topics() {
    }

    // ---- The event-sourcing / integration streams --------------------------
    public static final String HABIT_EVENTS = "lifeos.habit.events";
    public static final String EXPENSE_EVENTS = "lifeos.expense.events";
    public static final String PLANNING_EVENTS = "lifeos.planning.events";
    public static final String USER_EVENTS = "lifeos.user.events";
    public static final String GAMIFICATION_EVENTS = "lifeos.gamification.events";

    /** Published by the auth package so anything scheduling in local time knows the zone. */
    public static final class User {
        public static final String REGISTERED = "user.registered";
        public static final String LOGGED_IN = "user.logged-in";
        public static final String PROFILE_UPDATED = "user.profile-updated";

        private User() {
        }
    }

    // ---- Event type names -------------------------------------------------
    public static final class Habit {
        public static final String CREATED = "habit.created";
        public static final String UPDATED = "habit.updated";
        public static final String ARCHIVED = "habit.archived";
        public static final String DELETED = "habit.deleted";
        public static final String CHECKED_IN = "habit.checked-in";
        public static final String CHECK_IN_UNDONE = "habit.check-in-undone";
        public static final String STREAK_MILESTONE = "habit.streak-milestone";

        private Habit() {
        }
    }

    public static final class Expense {
        public static final String TRANSACTION_ADDED = "expense.transaction-added";
        public static final String TRANSACTION_UPDATED = "expense.transaction-updated";
        public static final String TRANSACTION_DELETED = "expense.transaction-deleted";
        public static final String BUDGET_SET = "expense.budget-set";
        public static final String BUDGET_EXCEEDED = "expense.budget-exceeded";

        private Expense() {
        }
    }

    public static final class Planning {
        public static final String TASK_CREATED = "planning.task-created";
        public static final String TASK_COMPLETED = "planning.task-completed";
        public static final String TASK_UPDATED = "planning.task-updated";
        public static final String TASK_DELETED = "planning.task-deleted";
        public static final String GOAL_CREATED = "planning.goal-created";
        public static final String GOAL_PROGRESSED = "planning.goal-progressed";
        public static final String FOCUS_SESSION_ENDED = "planning.focus-session-ended";

        private Planning() {
        }
    }

    public static final class Gamification {
        public static final String XP_AWARDED = "gamification.xp-awarded";
        public static final String LEVEL_UP = "gamification.level-up";
        public static final String ACHIEVEMENT_UNLOCKED = "gamification.achievement-unlocked";

        private Gamification() {
        }
    }
}
