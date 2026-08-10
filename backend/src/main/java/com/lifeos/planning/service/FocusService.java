package com.lifeos.planning.service;

import com.lifeos.common.event.DomainEvent;
import com.lifeos.common.event.EventPublisher;
import com.lifeos.common.event.Topics;
import com.lifeos.common.exception.ApiException;
import com.lifeos.planning.domain.FocusSession;
import com.lifeos.planning.domain.JournalEntry;
import com.lifeos.planning.domain.PlanningEnums.SessionType;
import com.lifeos.planning.dto.PlanningDtos.*;
import com.lifeos.planning.repo.FocusSessionRepository;
import com.lifeos.planning.repo.JournalRepository;
import com.lifeos.planning.repo.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pomodoro / deep-work timing, plus the daily journal. */
@Service
public class FocusService {

    private final FocusSessionRepository sessions;
    private final JournalRepository journals;
    private final TaskRepository tasks;
    private final EventPublisher events;

    public FocusService(FocusSessionRepository sessions, JournalRepository journals,
                        TaskRepository tasks, EventPublisher events) {
        this.sessions = sessions;
        this.journals = journals;
        this.tasks = tasks;
        this.events = events;
    }

    @Transactional
    public FocusResponse start(UUID userId, StartFocusRequest req) {
        // Starting a new session closes any stale one — a browser tab left open
        // overnight should not produce an eight-hour "focus session".
        sessions.openSessions(userId).forEach(open -> {
            open.setEndedAt(Instant.now());
            open.setActualMinutes(elapsedMinutes(open.getStartedAt(), Instant.now()));
            open.setCompleted(false);
            sessions.save(open);
        });

        SessionType type = req.type() == null ? SessionType.POMODORO : req.type();
        int planned = req.plannedMinutes() != null ? req.plannedMinutes() : defaultMinutes(type);

        FocusSession session = sessions.save(FocusSession.builder()
                .userId(userId)
                .taskId(req.taskId())
                .type(type)
                .sessionDate(LocalDate.now())
                .startedAt(Instant.now())
                .plannedMinutes(planned)
                .build());
        return FocusResponse.from(session);
    }

    @Transactional
    public FocusResponse end(UUID userId, UUID sessionId, EndFocusRequest req) {
        FocusSession session = sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> ApiException.notFound("Focus session", sessionId));
        if (session.getEndedAt() != null) {
            throw ApiException.badRequest("This session has already ended");
        }

        Instant now = Instant.now();
        int minutes = elapsedMinutes(session.getStartedAt(), now);

        session.setEndedAt(now);
        session.setActualMinutes(minutes);
        session.setFocusScore(req == null ? null : req.focusScore());
        session.setNote(req == null ? null : req.note());
        // Counts as completed when it ran at least 80% of the plan, or the caller says so.
        session.setCompleted(req != null && Boolean.TRUE.equals(req.completed())
                || minutes >= session.getPlannedMinutes() * 0.8);
        sessions.save(session);

        // Roll the time onto the task so estimate-vs-actual becomes real data.
        if (session.getTaskId() != null) {
            tasks.findByIdAndUserId(session.getTaskId(), userId).ifPresent(task -> {
                task.setActualMinutes(task.getActualMinutes() + minutes);
                tasks.save(task);
            });
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", session.getId().toString());
        payload.put("taskId", session.getTaskId() == null ? null : session.getTaskId().toString());
        payload.put("minutes", minutes);
        payload.put("type", session.getType().name());
        payload.put("completed", session.isCompleted());
        events.publish(Topics.PLANNING_EVENTS, DomainEvent.of(Topics.Planning.FOCUS_SESSION_ENDED,
                "FocusSession", session.getId().toString(), userId, 0L, payload));

        return FocusResponse.from(session);
    }

    @Transactional(readOnly = true)
    public FocusResponse current(UUID userId) {
        return sessions.openSessions(userId).stream().findFirst().map(FocusResponse::from).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<FocusResponse> history(UUID userId, LocalDate from, LocalDate to) {
        return sessions.findByUserIdAndSessionDateBetweenOrderByStartedAtAsc(userId, from, to)
                .stream().map(FocusResponse::from).toList();
    }

    // =============================================================== journal
    @Transactional
    public JournalResponse upsertJournal(UUID userId, JournalRequest req) {
        LocalDate date = req.entryDate() == null ? LocalDate.now() : req.entryDate();
        JournalEntry entry = journals.findByUserIdAndEntryDate(userId, date)
                .orElseGet(() -> JournalEntry.builder().userId(userId).entryDate(date).build());

        entry.setMood(req.mood());
        entry.setEnergy(req.energy());
        entry.setHighlights(req.highlights());
        entry.setGratitude(req.gratitude());
        entry.setNotes(req.notes());
        entry.setUpdatedAt(Instant.now());
        return JournalResponse.from(journals.save(entry));
    }

    @Transactional(readOnly = true)
    public List<JournalResponse> journalRange(UUID userId, LocalDate from, LocalDate to) {
        return journals.findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(userId, from, to)
                .stream().map(JournalResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public JournalResponse journalOn(UUID userId, LocalDate date) {
        return journals.findByUserIdAndEntryDate(userId, date).map(JournalResponse::from).orElse(null);
    }

    @Transactional
    public void deleteJournal(UUID userId, UUID id) {
        JournalEntry entry = journals.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Journal entry", id));
        journals.delete(entry);
    }

    private static int defaultMinutes(SessionType type) {
        return switch (type) {
            case POMODORO -> 25;
            case DEEP_WORK -> 90;
            case SHORT_BREAK -> 5;
            case LONG_BREAK -> 15;
        };
    }

    private static int elapsedMinutes(Instant from, Instant to) {
        return (int) Math.max(0, Duration.between(from, to).toMinutes());
    }
}
