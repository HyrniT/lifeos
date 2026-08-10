package com.lifeos.planning.messaging;

import com.lifeos.common.event.ReminderMessage;
import com.lifeos.planning.domain.Goal;
import com.lifeos.planning.domain.PlanningEnums.GoalStatus;
import com.lifeos.planning.domain.PlanningEnums.TaskStatus;
import com.lifeos.planning.domain.Task;
import com.lifeos.planning.domain.UserSettings;
import com.lifeos.planning.repo.GoalRepository;
import com.lifeos.planning.repo.TaskRepository;
import com.lifeos.planning.repo.PlanningUserSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.lifeos.platform.bus.ReminderBus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns deadlines into reminders — the "tell me before it is due" feature.
 *
 * Three design points worth knowing before changing anything here:
 *
 * **Local time, not server time.** A deadline is a wall-clock event. Every instant
 * is computed in the user's own zone, taken from the {@code user_settings}
 * projection; a server in UTC would otherwise fire a 09:00 reminder at 16:00 for
 * a user in +07.
 *
 * **The producer is deliberately naive.** It emits a candidate at every lead time
 * the system supports and lets notification-service drop the ones this user did
 * not ask for. That keeps user preferences in exactly one service and off this
 * scheduler's hot path.
 *
 * **Catch-up over precision.** The window looks back hours, not minutes, so a
 * restart or a slow deploy does not silently swallow a day's reminders. Emitting
 * the same reminder repeatedly is harmless because every message carries a dedupe
 * key that the consumer enforces with a unique index.
 */
@Component
public class DeadlineReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeadlineReminderScheduler.class);
    private static final Set<TaskStatus> CLOSED = Set.of(TaskStatus.DONE, TaskStatus.CANCELLED);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /** Stop nagging about a task nobody is going to do. */
    private static final int MAX_OVERDUE_DAYS = 7;

    private final TaskRepository tasks;
    private final GoalRepository goals;
    private final PlanningUserSettingsRepository userSettings;
    private final ReminderBus reminders;

    private final Duration catchUp;
    private final LocalTime defaultDueTime;
    private final LocalTime overdueNudgeHour;

    public DeadlineReminderScheduler(
            TaskRepository tasks, GoalRepository goals, PlanningUserSettingsRepository userSettings,
            ReminderBus reminders,
            @Value("${lifeos.reminders.catch-up-hours:3}") long catchUpHours,
            @Value("${lifeos.reminders.default-due-time:18:00}") String defaultDueTime,
            @Value("${lifeos.reminders.overdue-hour:09:00}") String overdueHour) {
        this.tasks = tasks;
        this.goals = goals;
        this.userSettings = userSettings;
        this.reminders = reminders;
        this.catchUp = Duration.ofHours(catchUpHours);
        this.defaultDueTime = LocalTime.parse(defaultDueTime);
        this.overdueNudgeHour = LocalTime.parse(overdueHour);
    }

    // ================================================================== tasks
    @Scheduled(fixedDelayString = "${lifeos.reminders.interval-ms:300000}")
    @Transactional(readOnly = true)
    public void scanTaskDeadlines() {
        Instant now = Instant.now();
        // Widest lead time is a week, and the overdue tail runs a week the other
        // way, so this window covers everything that could possibly fire today.
        LocalDate from = LocalDate.now().minusDays(MAX_OVERDUE_DAYS + 1L);
        LocalDate to = LocalDate.now().plusDays(9);

        List<Task> candidates = tasks.withDeadlineBetween(from, to, CLOSED);
        if (candidates.isEmpty()) {
            return;
        }

        Map<UUID, ZoneId> zones = new HashMap<>();
        int emitted = 0;

        for (Task task : candidates) {
            ZoneId zone = zones.computeIfAbsent(task.getUserId(), this::zoneFor);
            Instant deadline = deadlineOf(task, zone);

            emitted += emitLeadTimeReminders(task, deadline, now, zone);
            emitted += emitAtDeadline(task, deadline, now, zone);
            emitted += emitOverdueNudge(task, deadline, now, zone);
        }

        if (emitted > 0) {
            log.info("Emitted {} deadline reminder candidate(s) from {} task(s)", emitted, candidates.size());
        }
    }

    private int emitLeadTimeReminders(Task task, Instant deadline, Instant now, ZoneId zone) {
        int emitted = 0;
        for (int minutes : ReminderMessage.SUPPORTED_LEAD_MINUTES) {
            Instant fireAt = deadline.minus(Duration.ofMinutes(minutes));
            if (!isDue(fireAt, now)) {
                continue;
            }
            // Never warn "1 week before" about something already past.
            if (deadline.isBefore(now)) {
                continue;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("taskId", task.getId().toString());
            data.put("leadMinutes", minutes);
            data.put("priority", task.getPriority().name());
            data.put("dueDate", task.getDueDate().toString());

            publish(ReminderMessage.of(task.getUserId(), ReminderMessage.Kind.TASK_DUE_SOON,
                            "task:%s:lead:%d".formatted(task.getId(), minutes))
                    .title("Due in " + ReminderMessage.describeLead(minutes))
                    .body(task.getTitle() + deadlineSuffix(task, zone))
                    .icon("clock")
                    .severity(task.getPriority().name().equals("P1")
                            ? ReminderMessage.Severity.WARNING : ReminderMessage.Severity.INFO)
                    .deepLink("/planning?task=" + task.getId())
                    .data(data)
                    .build());
            emitted++;
        }
        return emitted;
    }

    private int emitAtDeadline(Task task, Instant deadline, Instant now, ZoneId zone) {
        if (!isDue(deadline, now)) {
            return 0;
        }
        publish(ReminderMessage.of(task.getUserId(), ReminderMessage.Kind.TASK_DUE,
                        "task:%s:due".formatted(task.getId()))
                .title("Due now")
                .body(task.getTitle())
                .icon("alarm-clock")
                .severity(ReminderMessage.Severity.WARNING)
                .deepLink("/planning?task=" + task.getId())
                .data(Map.of("taskId", task.getId().toString(),
                        "dueDate", task.getDueDate().toString()))
                .build());
        return 1;
    }

    /**
     * One nudge per day at a civilised hour while a task stays overdue, for at most
     * a week. Nagging every five minutes is how a user turns notifications off.
     */
    private int emitOverdueNudge(Task task, Instant deadline, Instant now, ZoneId zone) {
        if (!deadline.isBefore(now)) {
            return 0;
        }
        LocalDate today = now.atZone(zone).toLocalDate();
        long daysOverdue = ChronoUnit.DAYS.between(task.getDueDate(), today);
        if (daysOverdue < 1 || daysOverdue > MAX_OVERDUE_DAYS) {
            return 0;
        }

        Instant nudgeAt = today.atTime(overdueNudgeHour).atZone(zone).toInstant();
        if (!isDue(nudgeAt, now)) {
            return 0;
        }

        publish(ReminderMessage.of(task.getUserId(), ReminderMessage.Kind.TASK_OVERDUE,
                        "task:%s:overdue:%s".formatted(task.getId(), today))
                .title(daysOverdue == 1 ? "Overdue since yesterday" : "Overdue for " + daysOverdue + " days")
                .body(task.getTitle())
                .icon("alert-triangle")
                .severity(ReminderMessage.Severity.WARNING)
                .deepLink("/planning?task=" + task.getId())
                .data(Map.of("taskId", task.getId().toString(), "daysOverdue", daysOverdue))
                .build());
        return 1;
    }

    // ================================================================== goals
    @Scheduled(fixedDelayString = "${lifeos.reminders.goal-interval-ms:900000}")
    @Transactional(readOnly = true)
    public void scanGoalDeadlines() {
        Instant now = Instant.now();
        List<Goal> candidates = goals.findByStatusAndTargetDateBetween(
                GoalStatus.ACTIVE, LocalDate.now(), LocalDate.now().plusDays(8));

        for (Goal goal : candidates) {
            ZoneId zone = zoneFor(goal.getUserId());
            // A goal has no time of day, so the nudge lands with the morning batch.
            for (int daysBefore : new int[]{7, 1}) {
                LocalDate fireOn = goal.getTargetDate().minusDays(daysBefore);
                Instant fireAt = fireOn.atTime(overdueNudgeHour).atZone(zone).toInstant();
                if (!isDue(fireAt, now)) {
                    continue;
                }

                int remaining = (int) Math.round(
                        (1 - goal.progress()) * goal.getTargetValue().doubleValue());

                publish(ReminderMessage.of(goal.getUserId(), ReminderMessage.Kind.GOAL_DEADLINE,
                                "goal:%s:d%d".formatted(goal.getId(), daysBefore))
                        .title(daysBefore == 1 ? "Goal deadline tomorrow" : "Goal deadline in a week")
                        .body("%s — %d%% done, %d %s to go."
                                .formatted(goal.getTitle(), Math.round(goal.progress() * 100),
                                        Math.max(0, remaining), goal.getUnit()))
                        .icon("flag")
                        .severity(goal.progress() < 0.75
                                ? ReminderMessage.Severity.WARNING : ReminderMessage.Severity.INFO)
                        .deepLink("/goals")
                        .data(Map.of("goalId", goal.getId().toString(),
                                "progress", goal.progress(),
                                "daysBefore", daysBefore))
                        .build());
            }
        }
    }

    // ================================================================ helpers
    /**
     * True when {@code fireAt} has passed but not by more than the catch-up window.
     * The window is what makes a restart lose nothing; the dedupe key downstream is
     * what makes re-emitting harmless.
     */
    private boolean isDue(Instant fireAt, Instant now) {
        return !fireAt.isAfter(now) && fireAt.isAfter(now.minus(catchUp));
    }

    private Instant deadlineOf(Task task, ZoneId zone) {
        LocalTime time = task.getDueTime() != null ? task.getDueTime() : defaultDueTime;
        return task.getDueDate().atTime(time).atZone(zone).toInstant();
    }

    private String deadlineSuffix(Task task, ZoneId zone) {
        if (task.getDueTime() == null) {
            return "";
        }
        return " — due at " + task.getDueTime().format(TIME);
    }

    private ZoneId zoneFor(UUID userId) {
        return userSettings.findById(userId).map(UserSettings::zone).orElse(ZoneOffset.UTC);
    }

    private void publish(ReminderMessage message) {
        // The next scan re-emits anything that failed; the dedupe key on the
        // consuming side is what stops it arriving twice.
        reminders.sendReminder(message);
    }
}
