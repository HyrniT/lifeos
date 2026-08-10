package com.lifeos.habit.service;

import com.lifeos.common.event.Topics;
import com.lifeos.common.exception.ApiException;
import com.lifeos.habit.domain.HabitAggregate;
import com.lifeos.habit.domain.HabitEnums.Difficulty;
import com.lifeos.habit.dto.HabitDtos.*;
import com.lifeos.habit.eventstore.EventStore;
import com.lifeos.habit.eventstore.StoredEvent;
import com.lifeos.habit.projection.HabitProjector;
import com.lifeos.habit.readmodel.HabitLogRepository;
import com.lifeos.habit.readmodel.HabitView;
import com.lifeos.habit.readmodel.HabitViewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * The command side of the habit CQRS split.
 *
 * Every mutation follows the same shape: replay the aggregate, let it decide, append
 * the resulting events, project them. Nothing writes to the read model directly.
 */
@Service
public class HabitCommandService {

    private static final Logger log = LoggerFactory.getLogger(HabitCommandService.class);
    private static final String AGGREGATE = "Habit";

    private final EventStore eventStore;
    private final HabitProjector projector;
    private final HabitViewRepository habits;
    private final HabitLogRepository logs;
    private final StreakCalculator streaks;
    private final GamificationService gamification;

    public HabitCommandService(EventStore eventStore, HabitProjector projector,
                               HabitViewRepository habits, HabitLogRepository logs,
                               StreakCalculator streaks, GamificationService gamification) {
        this.eventStore = eventStore;
        this.projector = projector;
        this.habits = habits;
        this.logs = logs;
        this.streaks = streaks;
        this.gamification = gamification;
    }

    // ------------------------------------------------------------- create
    @Transactional
    public HabitResponse create(UUID userId, CreateHabitRequest req) {
        UUID habitId = UUID.randomUUID();
        HabitAggregate aggregate = HabitAggregate.replay(habitId, List.of());

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("name", req.name().trim());
        attributes.put("icon", orDefault(req.icon(), "target"));
        attributes.put("color", orDefault(req.color(), "#111111"));
        attributes.put("description", req.description());
        attributes.put("type", (req.type() == null ? com.lifeos.habit.domain.HabitEnums.HabitType.BUILD
                : req.type()).name());
        attributes.put("frequency", (req.frequency() == null
                ? com.lifeos.habit.domain.HabitEnums.Frequency.DAILY : req.frequency()).name());
        attributes.put("daysOfWeek", req.daysOfWeek() == null ? List.of() : new ArrayList<>(req.daysOfWeek()));
        attributes.put("intervalDays", req.intervalDays() == null ? 1 : req.intervalDays());
        attributes.put("targetPerPeriod", req.targetPerPeriod() == null ? 1 : req.targetPerPeriod());
        attributes.put("unit", (req.unit() == null ? com.lifeos.habit.domain.HabitEnums.Unit.TIMES
                : req.unit()).name());
        attributes.put("unitLabel", req.unitLabel());
        attributes.put("targetValue", req.targetValue() == null ? 1.0 : req.targetValue());
        attributes.put("reminderTime", req.reminderTime() == null ? null : req.reminderTime().toString());
        attributes.put("difficulty", (req.difficulty() == null ? Difficulty.MEDIUM : req.difficulty()).name());
        attributes.put("category", orDefault(req.category(), "general"));
        attributes.put("sortOrder", habits.maxSortOrder(userId) + 1);

        var pending = aggregate.decideCreate(userId, attributes);
        var stored = eventStore.append(habitId, AGGREGATE, userId, -1, Topics.HABIT_EVENTS, pending);
        stored.forEach(projector::project);

        log.info("User {} created habit '{}' ({})", userId, req.name(), habitId);
        return habits.findById(habitId)
                .map(v -> HabitResponse.from(v, false))
                .orElseThrow(() -> new IllegalStateException("Projection missing after create"));
    }

    // ------------------------------------------------------------- update
    @Transactional
    public HabitResponse update(UUID userId, UUID habitId, UpdateHabitRequest req) {
        HabitAggregate aggregate = loadOwned(userId, habitId);

        Map<String, Object> changes = new LinkedHashMap<>();
        putIfPresent(changes, "name", req.name() == null ? null : req.name().trim());
        putIfPresent(changes, "icon", req.icon());
        putIfPresent(changes, "color", req.color());
        putIfPresent(changes, "description", req.description());
        putIfPresent(changes, "type", req.type() == null ? null : req.type().name());
        putIfPresent(changes, "frequency", req.frequency() == null ? null : req.frequency().name());
        putIfPresent(changes, "daysOfWeek", req.daysOfWeek() == null ? null : new ArrayList<>(req.daysOfWeek()));
        putIfPresent(changes, "intervalDays", req.intervalDays());
        putIfPresent(changes, "targetPerPeriod", req.targetPerPeriod());
        putIfPresent(changes, "unit", req.unit() == null ? null : req.unit().name());
        putIfPresent(changes, "unitLabel", req.unitLabel());
        putIfPresent(changes, "targetValue", req.targetValue());
        putIfPresent(changes, "reminderTime", req.reminderTime() == null ? null : req.reminderTime().toString());
        putIfPresent(changes, "difficulty", req.difficulty() == null ? null : req.difficulty().name());
        putIfPresent(changes, "category", req.category());
        putIfPresent(changes, "sortOrder", req.sortOrder());

        var pending = aggregate.decideUpdate(changes);
        if (!pending.isEmpty()) {
            var stored = eventStore.append(habitId, AGGREGATE, userId, aggregate.version(),
                    Topics.HABIT_EVENTS, pending);
            stored.forEach(projector::project);
        }
        return read(userId, habitId);
    }

    @Transactional
    public HabitResponse setArchived(UUID userId, UUID habitId, boolean archived) {
        HabitAggregate aggregate = loadOwned(userId, habitId);
        var pending = aggregate.decideArchive(archived);
        if (!pending.isEmpty()) {
            eventStore.append(habitId, AGGREGATE, userId, aggregate.version(), Topics.HABIT_EVENTS, pending)
                    .forEach(projector::project);
        }
        return read(userId, habitId);
    }

    @Transactional
    public void delete(UUID userId, UUID habitId) {
        HabitAggregate aggregate = loadOwned(userId, habitId);
        var pending = aggregate.decideDelete();
        eventStore.append(habitId, AGGREGATE, userId, aggregate.version(), Topics.HABIT_EVENTS, pending)
                .forEach(projector::project);
        log.info("User {} deleted habit {}", userId, habitId);
    }

    @Transactional
    public void reorder(UUID userId, List<UUID> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            UUID id = orderedIds.get(i);
            HabitAggregate aggregate = loadOwned(userId, id);
            var pending = aggregate.decideUpdate(Map.of("sortOrder", i));
            eventStore.append(id, AGGREGATE, userId, aggregate.version(), Topics.HABIT_EVENTS, pending)
                    .forEach(projector::project);
        }
    }

    // ------------------------------------------------------------ check-in
    @Transactional
    public CheckInResponse checkIn(UUID userId, UUID habitId, CheckInRequest req) {
        HabitAggregate aggregate = loadOwned(userId, habitId);
        HabitView view = habits.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> ApiException.notFound("Habit", habitId));

        LocalDate date = req.date() == null ? LocalDate.now() : req.date();
        boolean alreadyLogged = aggregate.hasCheckInOn(date);

        // Project the streak this check-in will produce so the XP reward and the
        // milestone event can both be decided before anything is written.
        List<LocalDate> withNew = new ArrayList<>(
                logs.findByHabitIdOrderByLogDateDesc(habitId).stream()
                        .map(l -> l.getLogDate()).toList());
        if (!withNew.contains(date)) {
            withNew.add(date);
        }
        int resultingStreak = streaks.compute(view, withNew, LocalDate.now()).current();

        int xp = alreadyLogged ? 0 : GamificationService.xpFor(view.getDifficulty(), resultingStreak);

        var pending = aggregate.decideCheckIn(date, req.value() == null ? 1.0 : req.value(),
                req.note(), req.mood(), xp, resultingStreak);
        var stored = eventStore.append(habitId, AGGREGATE, userId, aggregate.version(),
                Topics.HABIT_EVENTS, pending);
        stored.forEach(projector::project);

        List<String> unlocked = alreadyLogged ? List.of() : gamification.awardCheckIn(userId, xp, date);
        boolean milestone = stored.stream()
                .anyMatch(s -> Topics.Habit.STREAK_MILESTONE.equals(s.getEventType()));

        HabitView refreshed = habits.findById(habitId).orElse(view);
        return new CheckInResponse(habitId, date, req.value() == null ? 1.0 : req.value(), xp,
                refreshed.getCurrentStreak(), milestone,
                StatsResponse.from(gamification.statsFor(userId)), unlocked);
    }

    @Transactional
    public HabitResponse undoCheckIn(UUID userId, UUID habitId, LocalDate date) {
        HabitAggregate aggregate = loadOwned(userId, habitId);
        LocalDate target = date == null ? LocalDate.now() : date;

        int xpToRevoke = logs.findByHabitIdAndLogDate(habitId, target)
                .map(l -> l.getXpAwarded())
                .orElse(0);

        var pending = aggregate.decideUndoCheckIn(target);
        eventStore.append(habitId, AGGREGATE, userId, aggregate.version(), Topics.HABIT_EVENTS, pending)
                .forEach(projector::project);

        gamification.revokeCheckIn(userId, xpToRevoke);
        return read(userId, habitId);
    }

    /** Operator escape hatch: rebuild this user's read model from the event stream. */
    @Transactional
    public int rebuildProjections(UUID userId) {
        return projector.rebuildFor(userId);
    }

    // ------------------------------------------------------------- helpers
    private HabitAggregate loadOwned(UUID userId, UUID habitId) {
        List<StoredEvent> history = eventStore.load(habitId);
        if (history.isEmpty()) {
            throw ApiException.notFound("Habit", habitId);
        }
        HabitAggregate aggregate = HabitAggregate.replay(habitId, history);
        if (!userId.equals(aggregate.userId())) {
            // Same response as "missing" so the API does not confirm the id exists.
            throw ApiException.notFound("Habit", habitId);
        }
        return aggregate;
    }

    private HabitResponse read(UUID userId, UUID habitId) {
        HabitView view = habits.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> ApiException.notFound("Habit", habitId));
        boolean doneToday = logs.findByHabitIdAndLogDate(habitId, LocalDate.now()).isPresent();
        return HabitResponse.from(view, doneToday);
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
