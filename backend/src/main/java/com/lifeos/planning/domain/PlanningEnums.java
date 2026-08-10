package com.lifeos.planning.domain;

public final class PlanningEnums {

    private PlanningEnums() {
    }

    /** P1 is "today, no matter what"; P4 is "someday". */
    public enum Priority {
        P1, P2, P3, P4
    }

    public enum TaskStatus {
        TODO, IN_PROGRESS, DONE, CANCELLED
    }

    public enum GoalStatus {
        ACTIVE, ACHIEVED, PAUSED, ABANDONED
    }

    public enum ProjectStatus {
        ACTIVE, ON_HOLD, COMPLETED, ARCHIVED
    }

    public enum SessionType {
        POMODORO, DEEP_WORK, SHORT_BREAK, LONG_BREAK
    }

    public enum Recurrence {
        NONE, DAILY, WEEKDAYS, WEEKLY, BIWEEKLY, MONTHLY, YEARLY
    }
}
