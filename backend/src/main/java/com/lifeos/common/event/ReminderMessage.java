package com.lifeos.common.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The contract between every reminder producer and the notification package.
 * Declared here so the two sides cannot drift.
 *
 * {@link #dedupeKey()} is the important field. Schedulers run on a fixed interval
 * and replay their window after a restart, so the same reminder will be produced
 * more than once by design. The consumer enforces uniqueness on this key, which
 * means producers can be naive and still not spam anyone — far more robust than a
 * "sent" flag on the source row, which cannot express "one reminder per lead time"
 * in the first place.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReminderMessage(
        UUID userId,
        String kind,
        String title,
        String body,
        String icon,
        String severity,

        /** Stable identity of this exact reminder, e.g. {@code task:<id>:T-24H}. */
        String dedupeKey,

        /** Where the notification should take the user, e.g. {@code /planning?task=<id>}. */
        String deepLink,

        /** Earliest delivery time; the dispatcher holds it until then. Null means now. */
        Instant notBefore,

        /** True for things the user asked to be interrupted for; bypasses quiet hours. */
        boolean urgent,

        Map<String, Object> data,
        Instant createdAt
) {

    public static Builder of(UUID userId, String kind, String dedupeKey) {
        return new Builder(userId, kind, dedupeKey);
    }

    /** Notification kinds. The user can switch each of these off independently. */
    public static final class Kind {
        public static final String TASK_DUE_SOON = "TASK_DUE_SOON";
        public static final String TASK_DUE = "TASK_DUE";
        public static final String TASK_OVERDUE = "TASK_OVERDUE";
        public static final String HABIT_DUE = "HABIT_DUE";
        public static final String HABIT_STREAK_AT_RISK = "HABIT_STREAK_AT_RISK";
        public static final String GOAL_DEADLINE = "GOAL_DEADLINE";
        public static final String DAILY_SUMMARY = "DAILY_SUMMARY";
        public static final String BUDGET_WARNING = "BUDGET_WARNING";
        public static final String BUDGET_EXCEEDED = "BUDGET_EXCEEDED";
        public static final String ACHIEVEMENT = "ACHIEVEMENT";
        public static final String STREAK_MILESTONE = "STREAK_MILESTONE";
        public static final String LEVEL_UP = "LEVEL_UP";
        public static final String SECURITY = "SECURITY";
        public static final String TEST = "TEST";

        private Kind() {
        }
    }

    /**
     * The lead times a deadline reminder can be sent at.
     *
     * The producing service emits a candidate at every one of these; the consumer
     * drops the ones the user did not ask for. Putting the decision on the consumer
     * side means the scheduler needs no knowledge of user preferences — and so no
     * synchronous call, and no third copy of the preference table.
     */
    public static final int[] SUPPORTED_LEAD_MINUTES = {
            10_080,  // 1 week
            4_320,   // 3 days
            1_440,   // 1 day
            480,     // 8 hours
            120,     // 2 hours
            60,      // 1 hour
            30,      // 30 minutes
            15       // 15 minutes
    };

    public static String describeLead(int minutes) {
        if (minutes % 10_080 == 0) {
            return (minutes / 10_080) + " week" + (minutes == 10_080 ? "" : "s");
        }
        if (minutes % 1_440 == 0) {
            return (minutes / 1_440) + " day" + (minutes == 1_440 ? "" : "s");
        }
        if (minutes % 60 == 0) {
            return (minutes / 60) + " hour" + (minutes == 60 ? "" : "s");
        }
        return minutes + " minutes";
    }

    public static final class Severity {
        public static final String INFO = "info";
        public static final String SUCCESS = "success";
        public static final String WARNING = "warning";
        public static final String CRITICAL = "critical";

        private Severity() {
        }
    }

    public static final class Builder {
        private final UUID userId;
        private final String kind;
        private final String dedupeKey;
        private String title = "";
        private String body = "";
        private String icon = "bell";
        private String severity = Severity.INFO;
        private String deepLink;
        private Instant notBefore;
        private boolean urgent;
        private Map<String, Object> data = Map.of();

        private Builder(UUID userId, String kind, String dedupeKey) {
            this.userId = userId;
            this.kind = kind;
            this.dedupeKey = dedupeKey;
        }

        public Builder title(String value) {
            this.title = value;
            return this;
        }

        public Builder body(String value) {
            this.body = value;
            return this;
        }

        public Builder icon(String value) {
            this.icon = value;
            return this;
        }

        public Builder severity(String value) {
            this.severity = value;
            return this;
        }

        public Builder deepLink(String value) {
            this.deepLink = value;
            return this;
        }

        public Builder notBefore(Instant value) {
            this.notBefore = value;
            return this;
        }

        public Builder urgent(boolean value) {
            this.urgent = value;
            return this;
        }

        public Builder data(Map<String, Object> value) {
            this.data = value == null ? Map.of() : value;
            return this;
        }

        public ReminderMessage build() {
            return new ReminderMessage(userId, kind, title, body, icon, severity, dedupeKey,
                    deepLink, notBefore, urgent, data, Instant.now());
        }
    }
}
