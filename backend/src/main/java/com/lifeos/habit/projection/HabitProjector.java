package com.lifeos.habit.projection;

import com.lifeos.common.event.Topics;
import com.lifeos.habit.domain.HabitEnums.*;
import com.lifeos.habit.eventstore.StoredEvent;
import com.lifeos.habit.eventstore.StoredEventRepository;
import com.lifeos.habit.readmodel.*;
import com.lifeos.habit.service.StreakCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * Folds the event stream into the query-side tables.
 *
 * Runs inside the command's transaction so a check-in is immediately visible to the
 * user who made it — read-your-writes matters far more here than the extra
 * throughput a fully async projector would buy. Cross-service consumers still get
 * the eventually-consistent path through the outbox.
 */
@Component
public class HabitProjector {

    private static final Logger log = LoggerFactory.getLogger(HabitProjector.class);

    private final HabitViewRepository habits;
    private final HabitLogRepository logs;
    private final StoredEventRepository events;
    private final StreakCalculator streaks;

    public HabitProjector(HabitViewRepository habits, HabitLogRepository logs,
                          StoredEventRepository events, StreakCalculator streaks) {
        this.habits = habits;
        this.logs = logs;
        this.events = events;
        this.streaks = streaks;
    }

    @Transactional
    public void project(StoredEvent event) {
        switch (event.getEventType()) {
            case Topics.Habit.CREATED -> onCreated(event);
            case Topics.Habit.UPDATED -> onUpdated(event);
            case Topics.Habit.ARCHIVED -> onArchived(event);
            case Topics.Habit.DELETED -> onDeleted(event);
            case Topics.Habit.CHECKED_IN -> onCheckedIn(event);
            case Topics.Habit.CHECK_IN_UNDONE -> onUndone(event);
            default -> log.debug("No projection for {}", event.getEventType());
        }
    }

    // ------------------------------------------------------------- handlers
    private void onCreated(StoredEvent e) {
        Map<String, Object> p = e.getPayload();
        HabitView view = HabitView.builder()
                .id(e.getAggregateId())
                .userId(e.getUserId())
                .name(str(p, "name", "Untitled"))
                .icon(str(p, "icon", "target"))
                .color(str(p, "color", "#111111"))
                .description(str(p, "description", null))
                .type(enumOf(HabitType.class, p.get("type"), HabitType.BUILD))
                .frequency(enumOf(Frequency.class, p.get("frequency"), Frequency.DAILY))
                .daysOfWeek(intSet(p.get("daysOfWeek")))
                .intervalDays(intOf(p.get("intervalDays"), 1))
                .targetPerPeriod(intOf(p.get("targetPerPeriod"), 1))
                .unit(enumOf(Unit.class, p.get("unit"), Unit.TIMES))
                .unitLabel(str(p, "unitLabel", null))
                .targetValue(doubleOf(p.get("targetValue"), 1.0))
                .reminderTime(timeOf(p.get("reminderTime")))
                .difficulty(enumOf(Difficulty.class, p.get("difficulty"), Difficulty.MEDIUM))
                .category(str(p, "category", "general"))
                .sortOrder(intOf(p.get("sortOrder"), 0))
                .versionNo(e.getSequenceNo())
                .createdAt(e.getOccurredAt())
                .updatedAt(e.getOccurredAt())
                .build();
        habits.save(view);
    }

    private void onUpdated(StoredEvent e) {
        habits.findById(e.getAggregateId()).ifPresent(view -> {
            Map<String, Object> p = e.getPayload();
            if (p.containsKey("name")) {
                view.setName(str(p, "name", view.getName()));
            }
            if (p.containsKey("icon")) {
                view.setIcon(str(p, "icon", view.getIcon()));
            }
            if (p.containsKey("color")) {
                view.setColor(str(p, "color", view.getColor()));
            }
            if (p.containsKey("description")) {
                view.setDescription(str(p, "description", null));
            }
            if (p.containsKey("type")) {
                view.setType(enumOf(HabitType.class, p.get("type"), view.getType()));
            }
            if (p.containsKey("frequency")) {
                view.setFrequency(enumOf(Frequency.class, p.get("frequency"), view.getFrequency()));
            }
            if (p.containsKey("daysOfWeek")) {
                view.setDaysOfWeek(intSet(p.get("daysOfWeek")));
            }
            if (p.containsKey("intervalDays")) {
                view.setIntervalDays(intOf(p.get("intervalDays"), view.getIntervalDays()));
            }
            if (p.containsKey("targetPerPeriod")) {
                view.setTargetPerPeriod(intOf(p.get("targetPerPeriod"), view.getTargetPerPeriod()));
            }
            if (p.containsKey("unit")) {
                view.setUnit(enumOf(Unit.class, p.get("unit"), view.getUnit()));
            }
            if (p.containsKey("unitLabel")) {
                view.setUnitLabel(str(p, "unitLabel", null));
            }
            if (p.containsKey("targetValue")) {
                view.setTargetValue(doubleOf(p.get("targetValue"), view.getTargetValue()));
            }
            if (p.containsKey("reminderTime")) {
                view.setReminderTime(timeOf(p.get("reminderTime")));
            }
            if (p.containsKey("difficulty")) {
                view.setDifficulty(enumOf(Difficulty.class, p.get("difficulty"), view.getDifficulty()));
            }
            if (p.containsKey("category")) {
                view.setCategory(str(p, "category", view.getCategory()));
            }
            if (p.containsKey("sortOrder")) {
                view.setSortOrder(intOf(p.get("sortOrder"), view.getSortOrder()));
            }

            view.setVersionNo(e.getSequenceNo());
            view.setUpdatedAt(e.getOccurredAt());
            habits.save(view);

            // Frequency drives the streak definition, so recompute when it changes.
            if (p.containsKey("frequency") || p.containsKey("daysOfWeek")
                    || p.containsKey("intervalDays") || p.containsKey("targetPerPeriod")) {
                recomputeDerived(view);
            }
        });
    }

    private void onArchived(StoredEvent e) {
        habits.findById(e.getAggregateId()).ifPresent(view -> {
            view.setArchived(Boolean.TRUE.equals(e.getPayload().get("archived")));
            view.setVersionNo(e.getSequenceNo());
            view.setUpdatedAt(e.getOccurredAt());
            habits.save(view);
        });
    }

    private void onDeleted(StoredEvent e) {
        // The event stream keeps the history; the read model does not need a tombstone.
        logs.deleteByHabitId(e.getAggregateId());
        habits.deleteById(e.getAggregateId());
    }

    private void onCheckedIn(StoredEvent e) {
        Map<String, Object> p = e.getPayload();
        LocalDate date = LocalDate.parse(String.valueOf(p.get("date")));
        UUID habitId = e.getAggregateId();

        HabitLogView entry = logs.findByHabitIdAndLogDate(habitId, date).orElseGet(() ->
                HabitLogView.builder()
                        .habitId(habitId)
                        .userId(e.getUserId())
                        .logDate(date)
                        .createdAt(e.getOccurredAt())
                        .build());

        entry.setValue(doubleOf(p.get("value"), 1.0));
        entry.setNote(p.get("note") == null ? null : String.valueOf(p.get("note")));
        entry.setMood(p.get("mood") == null ? null : intOf(p.get("mood"), 3));
        entry.setXpAwarded(intOf(p.get("xp"), 0));
        logs.save(entry);

        habits.findById(habitId).ifPresent(view -> {
            view.setVersionNo(e.getSequenceNo());
            view.setUpdatedAt(e.getOccurredAt());
            recomputeDerived(view);
        });
    }

    private void onUndone(StoredEvent e) {
        LocalDate date = LocalDate.parse(String.valueOf(e.getPayload().get("date")));
        logs.findByHabitIdAndLogDate(e.getAggregateId(), date).ifPresent(logs::delete);

        habits.findById(e.getAggregateId()).ifPresent(view -> {
            view.setVersionNo(e.getSequenceNo());
            view.setUpdatedAt(e.getOccurredAt());
            recomputeDerived(view);
        });
    }

    // ------------------------------------------------------------ derived
    /** Recomputes streaks, totals and completion rate from the log table. */
    public void recomputeDerived(HabitView view) {
        List<HabitLogView> entries = logs.findByHabitIdOrderByLogDateDesc(view.getId());
        List<LocalDate> dates = entries.stream().map(HabitLogView::getLogDate).toList();
        Set<LocalDate> dateSet = new HashSet<>(dates);
        LocalDate today = LocalDate.now();

        StreakCalculator.Streaks s = streaks.compute(view, dates, today);
        view.setCurrentStreak(s.current());
        view.setLongestStreak(Math.max(view.getLongestStreak(), s.longest()));
        view.setTotalCheckIns(entries.size());
        view.setLastCheckInDate(dates.isEmpty() ? null : Collections.max(dates));
        view.setCompletionRate30d(streaks.completionRate(view, dateSet, today, 30));
        habits.save(view);
    }

    /**
     * Drops and rebuilds every projection for a user straight from the event store.
     * The safety net that makes a read model genuinely disposable.
     */
    @Transactional
    public int rebuildFor(UUID userId) {
        habits.findByUserIdOrderBySortOrderAscCreatedAtAsc(userId)
                .forEach(v -> logs.deleteByHabitId(v.getId()));
        habits.deleteAll(habits.findByUserIdOrderBySortOrderAscCreatedAtAsc(userId));

        List<StoredEvent> stream = events.findByUserIdOrderByOccurredAtAsc(userId);
        stream.forEach(this::project);
        log.info("Rebuilt {} projection event(s) for user {}", stream.size(), userId);
        return stream.size();
    }

    // ------------------------------------------------------------ coercion
    private static String str(Map<String, Object> p, String key, String fallback) {
        Object v = p.get(key);
        return v == null ? fallback : String.valueOf(v);
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, Object raw, E fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, String.valueOf(raw));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private static int intOf(Object raw, Integer fallback) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            return raw == null ? (fallback == null ? 0 : fallback) : Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return fallback == null ? 0 : fallback;
        }
    }

    private static double doubleOf(Object raw, Double fallback) {
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return raw == null ? (fallback == null ? 0 : fallback) : Double.parseDouble(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return fallback == null ? 0 : fallback;
        }
    }

    private static LocalTime timeOf(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return LocalTime.parse(String.valueOf(raw));
        } catch (Exception ex) {
            return null;
        }
    }

    private static Set<Integer> intSet(Object raw) {
        Set<Integer> result = new LinkedHashSet<>();
        if (raw instanceof Collection<?> collection) {
            collection.forEach(v -> {
                if (v instanceof Number n) {
                    result.add(n.intValue());
                } else if (v != null) {
                    try {
                        result.add(Integer.parseInt(String.valueOf(v)));
                    } catch (NumberFormatException ignored) {
                        // Skip values that are not day numbers.
                    }
                }
            });
        }
        return result;
    }
}
