package com.lifeos.analytics.projection;

import com.lifeos.analytics.model.DailyRollup;
import com.lifeos.analytics.model.EventRecord;
import com.lifeos.analytics.repo.DailyRollupRepository;
import com.lifeos.analytics.repo.EventRecordRepository;
import com.lifeos.common.event.DomainEvent;
import com.lifeos.common.event.Topics;
import com.lifeos.platform.bus.TopicEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * The CQRS query-side projector: events in, read models out.
 *
 * Everything here is idempotent, and still needs to be. The outbox relay in front
 * of it retries rows it could not dispatch, so the same event will arrive twice
 * after a failure. Each event id is the primary key of its archive row, and a
 * repeat is detected there before any counter moves.
 *
 * Each handler runs in its own transaction. A projection that fails must not roll
 * back the write that produced the event — the one guarantee the broker used to
 * provide simply by being somewhere else.
 */
@Component
public class EventProjector {

    private static final Logger log = LoggerFactory.getLogger(EventProjector.class);

    private final DailyRollupRepository rollups;
    private final EventRecordRepository records;

    public EventProjector(DailyRollupRepository rollups, EventRecordRepository records) {
        this.rollups = rollups;
        this.records = records;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onHabitEvent(TopicEvent envelope) {
        if (!Topics.HABIT_EVENTS.equals(envelope.topic())) {
            return;
        }
        DomainEvent event = envelope.event();
        if (!archive(event)) {
            return;
        }
        switch (event.eventType()) {
            case Topics.Habit.CHECKED_IN -> {
                LocalDate date = parseDate(event.get("date", String.class), event.occurredAt());
                mutate(event.userId(), date, r -> {
                    r.setHabitCheckIns(r.getHabitCheckIns() + 1);
                    Integer xp = event.get("xp", Integer.class);
                    r.setXpEarned(r.getXpEarned() + (xp == null ? 0 : xp));
                    Integer streak = event.get("streak", Integer.class);
                    if (streak != null) {
                        r.setBestStreak(Math.max(r.getBestStreak(), streak));
                    }
                    Integer mood = event.get("mood", Integer.class);
                    if (mood != null) {
                        r.setMood(mood);
                    }
                });
            }
            case Topics.Habit.CHECK_IN_UNDONE -> {
                LocalDate date = parseDate(event.get("date", String.class), event.occurredAt());
                mutate(event.userId(), date, r -> r.setHabitCheckIns(Math.max(0, r.getHabitCheckIns() - 1)));
            }
            default -> log.trace("Habit event {} archived without rollup impact", event.eventType());
        }
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onExpenseEvent(TopicEvent envelope) {
        if (!Topics.EXPENSE_EVENTS.equals(envelope.topic())) {
            return;
        }
        DomainEvent event = envelope.event();
        if (!archive(event)) {
            return;
        }
        if (!Topics.Expense.TRANSACTION_ADDED.equals(event.eventType())) {
            return;
        }
        LocalDate date = parseDate(event.get("occurredOn", String.class), event.occurredAt());
        String txType = event.get("txType", String.class);
        BigDecimal amount = toBigDecimal(event.payload().get("amount"));

        // Transfers move money between the user's own accounts; counting them would
        // inflate both totals for no reason.
        if ("TRANSFER".equals(txType)) {
            return;
        }

        mutate(event.userId(), date, r -> {
            r.setTransactionCount(r.getTransactionCount() + 1);
            if ("INCOME".equals(txType)) {
                r.setIncomeTotal(r.getIncomeTotal().add(amount));
            } else {
                r.setExpenseTotal(r.getExpenseTotal().add(amount));
                String categoryId = event.get("categoryId", String.class);
                if (categoryId != null) {
                    r.getSpendByCategory().merge(categoryId, amount, BigDecimal::add);
                }
            }
        });
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPlanningEvent(TopicEvent envelope) {
        if (!Topics.PLANNING_EVENTS.equals(envelope.topic())) {
            return;
        }
        DomainEvent event = envelope.event();
        if (!archive(event)) {
            return;
        }
        LocalDate date = event.occurredAt().atZone(ZoneOffset.UTC).toLocalDate();

        switch (event.eventType()) {
            case Topics.Planning.TASK_CREATED ->
                    mutate(event.userId(), date, r -> r.setTasksCreated(r.getTasksCreated() + 1));
            case Topics.Planning.TASK_COMPLETED ->
                    mutate(event.userId(), date, r -> r.setTasksCompleted(r.getTasksCompleted() + 1));
            case Topics.Planning.FOCUS_SESSION_ENDED -> {
                Integer minutes = event.get("minutes", Integer.class);
                boolean completed = Boolean.TRUE.equals(event.payload().get("completed"));
                if (completed && minutes != null) {
                    mutate(event.userId(), date, r -> {
                        r.setFocusMinutes(r.getFocusMinutes() + minutes);
                        r.setFocusSessions(r.getFocusSessions() + 1);
                    });
                }
            }
            default -> log.trace("Planning event {} archived without rollup impact", event.eventType());
        }
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMiscEvent(TopicEvent envelope) {
        if (Topics.GAMIFICATION_EVENTS.equals(envelope.topic())
                || Topics.USER_EVENTS.equals(envelope.topic())) {
            archive(envelope.event());
        }
    }

    /**
     * Stands in for the TTL index the archive had as a Mongo collection. Two years,
     * the same horizon, deleted nightly instead of continuously.
     */
    @Scheduled(cron = "${lifeos.analytics.retention-cron:0 20 3 * * *}")
    @Transactional
    public void expireOldRecords() {
        int removed = records.deleteOlderThan(Instant.now().minus(Duration.ofDays(730)));
        if (removed > 0) {
            log.info("Expired {} archived event(s) older than two years", removed);
        }
    }

    // =============================================================== helpers
    /**
     * Archives the event and reports whether it is new.
     *
     * @return {@code false} when this event id has already been processed, which is
     *         the signal for the caller to skip every counter update.
     */
    private boolean archive(DomainEvent event) {
        String id = event.eventId().toString();
        if (records.existsById(id)) {
            log.debug("Duplicate delivery of {} ignored", id);
            return false;
        }
        records.save(EventRecord.builder()
                .id(id)
                .userId(event.userId())
                .eventType(event.eventType())
                .aggregateType(event.aggregateType())
                .aggregateId(event.aggregateId())
                .payload(event.payload())
                .occurredAt(event.occurredAt())
                .receivedAt(Instant.now())
                .build());
        return true;
    }

    private void mutate(UUID userId, LocalDate date, java.util.function.Consumer<DailyRollup> change) {
        DailyRollup rollup = rollups.findByUserIdAndDate(userId, date)
                .orElseGet(() -> DailyRollup.builder()
                        .id(DailyRollup.idFor(userId, date))
                        .userId(userId)
                        .date(date)
                        .build());
        change.accept(rollup);
        rollup.setUpdatedAt(Instant.now());
        rollups.save(rollup);
    }

    private static LocalDate parseDate(String raw, Instant fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback.atZone(ZoneOffset.UTC).toLocalDate();
        }
        try {
            return LocalDate.parse(raw);
        } catch (Exception ex) {
            return fallback.atZone(ZoneOffset.UTC).toLocalDate();
        }
    }

    private static BigDecimal toBigDecimal(Object raw) {
        if (raw instanceof BigDecimal bd) {
            return bd;
        }
        if (raw instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return raw == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }
}
