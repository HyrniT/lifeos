package com.lifeos.planning.messaging;

import com.lifeos.common.event.ReminderMessage;
import com.lifeos.common.event.SummaryRequest;
import com.lifeos.planning.domain.PlanningEnums.TaskStatus;
import com.lifeos.planning.domain.Task;
import com.lifeos.planning.repo.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.lifeos.platform.bus.ReminderBus;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Answers "build this user's morning summary".
 *
 * The notification context decides *when* — it owns the preferred time and the
 * timezone. This one knows *what* is due. Keeping the split means neither package
 * reaches into the other's tables, and the preference table is still stored once.
 */
@Component
public class DailySummaryListener {

    private static final Logger log = LoggerFactory.getLogger(DailySummaryListener.class);
    private static final Set<TaskStatus> CLOSED = Set.of(TaskStatus.DONE, TaskStatus.CANCELLED);

    private final TaskRepository tasks;
    private final ReminderBus reminders;

    public DailySummaryListener(TaskRepository tasks, ReminderBus reminders) {
        this.tasks = tasks;
        this.reminders = reminders;
    }

    @EventListener
    @Transactional(readOnly = true)
    public void onSummaryRequest(SummaryRequest request) {
        try {
            List<Task> open = tasks.openThrough(request.userId(), request.localDate(), CLOSED);

            long overdue = open.stream()
                    .filter(t -> t.getDueDate().isBefore(request.localDate())).count();
            long dueToday = open.stream()
                    .filter(t -> t.getDueDate().isEqual(request.localDate())).count();

            // Nothing to report is not worth a notification. Silence on a clear day
            // is what keeps the digest credible on a busy one.
            if (overdue == 0 && dueToday == 0) {
                log.debug("No summary for {} on {} — nothing due", request.userId(), request.localDate());
                return;
            }

            String headline = dueToday > 0
                    ? "%d task%s due today".formatted(dueToday, dueToday == 1 ? "" : "s")
                    : "%d overdue task%s".formatted(overdue, overdue == 1 ? "" : "s");

            StringBuilder body = new StringBuilder();
            if (overdue > 0 && dueToday > 0) {
                body.append(overdue).append(overdue == 1 ? " is overdue. " : " are overdue. ");
            }
            open.stream()
                    .filter(t -> !t.getDueDate().isAfter(request.localDate()))
                    .limit(3)
                    .forEach(t -> body.append("• ").append(t.getTitle()).append("  "));
            if (dueToday + overdue > 3) {
                body.append("and ").append(dueToday + overdue - 3).append(" more.");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("dueToday", dueToday);
            data.put("overdue", overdue);
            data.put("date", request.localDate().toString());

            reminders.sendReminder(
                    ReminderMessage.of(request.userId(), ReminderMessage.Kind.DAILY_SUMMARY,
                                    "summary:%s:%s".formatted(request.userId(), request.localDate()))
                            .title(headline)
                            .body(body.toString().trim())
                            .icon("sunrise")
                            .severity(overdue > 0
                                    ? ReminderMessage.Severity.WARNING : ReminderMessage.Severity.INFO)
                            .deepLink("/planning")
                            .data(data)
                            .build());

        } catch (Exception ex) {
            log.error("Could not build the daily summary for {}: {}", request.userId(), ex.getMessage(), ex);
        }
    }
}
