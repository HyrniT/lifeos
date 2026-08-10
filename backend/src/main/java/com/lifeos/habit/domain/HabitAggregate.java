package com.lifeos.habit.domain;

import com.lifeos.common.event.Topics;
import com.lifeos.common.exception.ApiException;
import com.lifeos.habit.eventstore.EventStore.PendingEvent;
import com.lifeos.habit.eventstore.StoredEvent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The write model.
 *
 * State lives only as a fold over the event stream — {@link #replay} rebuilds it,
 * the {@code decide*} methods validate a command against that state and return the
 * events it produces. Nothing here touches a database or a framework, which is what
 * makes the invariants (no double check-in, no writes to a deleted habit) testable
 * in isolation.
 */
public final class HabitAggregate {

    private final UUID id;
    private UUID userId;
    private long version;

    private boolean created;
    private boolean deleted;
    private boolean archived;
    private String name;
    private HabitEnums.Difficulty difficulty = HabitEnums.Difficulty.MEDIUM;

    /** Days already checked in, so a replayed command cannot double-count. */
    private final Set<LocalDate> checkedDates = new HashSet<>();

    private HabitAggregate(UUID id) {
        this.id = id;
    }

    public static HabitAggregate replay(UUID id, List<StoredEvent> history) {
        HabitAggregate aggregate = new HabitAggregate(id);
        history.forEach(aggregate::apply);
        return aggregate;
    }

    // ------------------------------------------------------------- folding
    private void apply(StoredEvent event) {
        this.version = event.getSequenceNo();
        this.userId = event.getUserId();
        Map<String, Object> p = event.getPayload();

        switch (event.getEventType()) {
            case Topics.Habit.CREATED -> {
                created = true;
                name = str(p, "name");
                difficulty = difficultyOf(p.get("difficulty"));
            }
            case Topics.Habit.UPDATED -> {
                if (p.containsKey("name")) {
                    name = str(p, "name");
                }
                if (p.containsKey("difficulty")) {
                    difficulty = difficultyOf(p.get("difficulty"));
                }
            }
            case Topics.Habit.ARCHIVED -> archived = Boolean.TRUE.equals(p.get("archived"));
            case Topics.Habit.DELETED -> deleted = true;
            case Topics.Habit.CHECKED_IN -> checkedDates.add(LocalDate.parse(str(p, "date")));
            case Topics.Habit.CHECK_IN_UNDONE -> checkedDates.remove(LocalDate.parse(str(p, "date")));
            default -> {
                // Unknown event types are ignored on purpose: an older instance must
                // still be able to replay a stream written by a newer one.
            }
        }
    }

    // ------------------------------------------------------------ decisions
    public List<PendingEvent> decideCreate(UUID owner, Map<String, Object> attributes) {
        if (created) {
            throw ApiException.conflict("This habit already exists");
        }
        this.userId = owner;
        return List.of(new PendingEvent(Topics.Habit.CREATED, attributes));
    }

    public List<PendingEvent> decideUpdate(Map<String, Object> changes) {
        requireLive();
        if (changes.isEmpty()) {
            return List.of();
        }
        return List.of(new PendingEvent(Topics.Habit.UPDATED, changes));
    }

    public List<PendingEvent> decideArchive(boolean archive) {
        requireLive();
        if (archived == archive) {
            return List.of();
        }
        return List.of(new PendingEvent(Topics.Habit.ARCHIVED, Map.of("archived", archive)));
    }

    public List<PendingEvent> decideDelete() {
        requireExists();
        if (deleted) {
            return List.of();
        }
        return List.of(new PendingEvent(Topics.Habit.DELETED, Map.of()));
    }

    /**
     * Records a check-in and, when the resulting streak lands on a milestone, an
     * extra event so notifications and analytics do not each have to recompute it.
     */
    public List<PendingEvent> decideCheckIn(LocalDate date, double value, String note,
                                            Integer mood, int xpAwarded, int resultingStreak) {
        requireLive();
        if (date.isAfter(LocalDate.now().plusDays(1))) {
            throw ApiException.badRequest("You cannot check in for a future date");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("date", date.toString());
        payload.put("value", value);
        payload.put("note", note);
        payload.put("mood", mood);
        payload.put("xp", xpAwarded);
        payload.put("streak", resultingStreak);
        payload.put("alreadyLogged", checkedDates.contains(date));

        List<PendingEvent> result = new ArrayList<>();
        result.add(new PendingEvent(Topics.Habit.CHECKED_IN, payload));

        if (isMilestone(resultingStreak) && !checkedDates.contains(date)) {
            result.add(new PendingEvent(Topics.Habit.STREAK_MILESTONE,
                    Map.of("streak", resultingStreak, "habitName", name == null ? "" : name)));
        }
        return result;
    }

    public List<PendingEvent> decideUndoCheckIn(LocalDate date) {
        requireLive();
        if (!checkedDates.contains(date)) {
            throw ApiException.badRequest("There is no check-in on " + date + " to undo");
        }
        return List.of(new PendingEvent(Topics.Habit.CHECK_IN_UNDONE, Map.of("date", date.toString())));
    }

    /** 7, 14, 30, 50, 100, 200, 365 … the points worth celebrating. */
    static boolean isMilestone(int streak) {
        return streak == 7 || streak == 14 || streak == 30 || streak == 50
                || streak == 100 || streak == 200 || streak == 365
                || (streak > 365 && streak % 365 == 0);
    }

    // -------------------------------------------------------------- guards
    private void requireExists() {
        if (!created) {
            throw ApiException.notFound("Habit", id);
        }
    }

    private void requireLive() {
        requireExists();
        if (deleted) {
            throw ApiException.notFound("Habit", id);
        }
    }

    // ------------------------------------------------------------ accessors
    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public long version() {
        return version;
    }

    public boolean exists() {
        return created && !deleted;
    }

    public boolean isArchived() {
        return archived;
    }

    public HabitEnums.Difficulty difficulty() {
        return difficulty;
    }

    public boolean hasCheckInOn(LocalDate date) {
        return checkedDates.contains(date);
    }

    private static String str(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static HabitEnums.Difficulty difficultyOf(Object raw) {
        if (raw == null) {
            return HabitEnums.Difficulty.MEDIUM;
        }
        try {
            return HabitEnums.Difficulty.valueOf(String.valueOf(raw));
        } catch (IllegalArgumentException ex) {
            return HabitEnums.Difficulty.MEDIUM;
        }
    }
}
