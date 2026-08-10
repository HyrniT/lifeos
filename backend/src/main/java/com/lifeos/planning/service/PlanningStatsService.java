package com.lifeos.planning.service;

import com.lifeos.planning.domain.FocusSession;
import com.lifeos.planning.domain.Goal;
import com.lifeos.planning.domain.PlanningEnums.GoalStatus;
import com.lifeos.planning.domain.PlanningEnums.Priority;
import com.lifeos.planning.domain.PlanningEnums.TaskStatus;
import com.lifeos.planning.domain.Task;
import com.lifeos.planning.dto.PlanningDtos.PlanningStatistics;
import com.lifeos.planning.dto.PlanningDtos.ProductivityPoint;
import com.lifeos.planning.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Productivity analytics.
 *
 * The focus-by-hour histogram is the one people act on: it shows when their deep
 * work actually happens, which is usually not when they assume.
 */
@Service
public class PlanningStatsService {

    private final TaskRepository tasks;
    private final GoalRepository goals;
    private final FocusSessionRepository sessions;
    private final JournalRepository journals;

    public PlanningStatsService(TaskRepository tasks, GoalRepository goals,
                                FocusSessionRepository sessions, JournalRepository journals) {
        this.tasks = tasks;
        this.goals = goals;
        this.sessions = sessions;
        this.journals = journals;
    }

    @Transactional(readOnly = true)
    public PlanningStatistics compute(UUID userId, int windowDays) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(windowDays - 1L);
        Instant since7 = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant since30 = Instant.now().minus(30, ChronoUnit.DAYS);

        long open = tasks.countByUserIdAndStatus(userId, TaskStatus.TODO)
                + tasks.countByUserIdAndStatus(userId, TaskStatus.IN_PROGRESS);
        long done = tasks.countByUserIdAndStatus(userId, TaskStatus.DONE);

        List<Task> allOpen = tasks.search(userId, null, null, null, false, null, TaskStatus.DONE);
        long overdue = allOpen.stream().filter(Task::isOverdue).count();

        List<Task> completed7 = tasks.completedSince(userId, since7, TaskStatus.DONE);
        List<Task> completed30 = tasks.completedSince(userId, since30, TaskStatus.DONE);

        // Completion rate = finished in the window ÷ (finished + still-open-and-due).
        long dueInWindow = allOpen.stream()
                .filter(t -> t.getDueDate() != null && !t.getDueDate().isBefore(from)
                        && !t.getDueDate().isAfter(today))
                .count();
        double completionRate = (completed30.size() + dueInWindow) == 0 ? 0
                : Math.round((double) completed30.size() / (completed30.size() + dueInWindow) * 1000) / 1000.0;

        Long focus7 = sessions.focusMinutes(userId, today.minusDays(6), today);
        Long focus30 = sessions.focusMinutes(userId, today.minusDays(29), today);
        long totalSessions = sessions.countByUserIdAndCompletedTrue(userId);

        List<FocusSession> windowSessions =
                sessions.findByUserIdAndSessionDateBetweenOrderByStartedAtAsc(userId, from, today);
        double avgSession = windowSessions.stream()
                .filter(FocusSession::isCompleted)
                .mapToInt(FocusSession::getActualMinutes)
                .average().orElse(0);

        // --- distributions ---
        Map<String, Long> byQuadrant = new LinkedHashMap<>();
        for (int q = 1; q <= 4; q++) {
            final int quadrant = q;
            byQuadrant.put("Q" + q, allOpen.stream()
                    .filter(t -> t.eisenhowerQuadrant() == quadrant).count());
        }

        Map<String, Long> byPriority = new LinkedHashMap<>();
        for (Priority p : Priority.values()) {
            byPriority.put(p.name(), allOpen.stream().filter(t -> t.getPriority() == p).count());
        }

        // --- timeline ---
        Map<LocalDate, long[]> perDay = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            perDay.put(d, new long[]{0, 0, 0});
        }
        completed30.forEach(t -> {
            LocalDate d = t.getCompletedAt().atZone(ZoneOffset.UTC).toLocalDate();
            long[] row = perDay.get(d);
            if (row != null) {
                row[0]++;
            }
        });
        for (Object[] row : sessions.dailyFocus(userId, from, today)) {
            long[] target = perDay.get((LocalDate) row[0]);
            if (target != null) {
                target[2] = ((Number) row[1]).longValue();
            }
        }

        List<ProductivityPoint> timeline = perDay.entrySet().stream()
                .map(e -> new ProductivityPoint(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .toList();

        // --- focus by hour of day ---
        Map<String, Long> focusByHour = new LinkedHashMap<>();
        for (int h = 0; h < 24; h++) {
            focusByHour.put(String.format("%02d", h), 0L);
        }
        windowSessions.stream().filter(FocusSession::isCompleted).forEach(s -> {
            String hour = String.format("%02d", s.getStartedAt().atZone(ZoneOffset.UTC).getHour());
            focusByHour.merge(hour, (long) s.getActualMinutes(), Long::sum);
        });

        // --- most productive weekday ---
        Map<DayOfWeek, Long> perWeekday = new EnumMap<>(DayOfWeek.class);
        completed30.forEach(t -> perWeekday.merge(
                t.getCompletedAt().atZone(ZoneOffset.UTC).getDayOfWeek(), 1L, Long::sum));
        String bestDay = perWeekday.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> e.getKey().getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .orElse("—");

        // --- goals ---
        long activeGoals = goals.countByUserIdAndStatus(userId, GoalStatus.ACTIVE);
        long achievedGoals = goals.countByUserIdAndStatus(userId, GoalStatus.ACHIEVED);
        double avgGoalProgress = goals.findByUserIdAndStatusOrderByTargetDateAsc(userId, GoalStatus.ACTIVE)
                .stream().mapToDouble(Goal::progress).average().orElse(0);

        // --- journal averages ---
        var entries = journals.findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(userId, from, today);
        Double avgMood = entries.stream().filter(e -> e.getMood() != null)
                .mapToInt(e -> e.getMood()).average().stream().boxed().findFirst().orElse(null);
        Double avgEnergy = entries.stream().filter(e -> e.getEnergy() != null)
                .mapToInt(e -> e.getEnergy()).average().stream().boxed().findFirst().orElse(null);

        return new PlanningStatistics(
                open, done, overdue, completed7.size(), completed30.size(), completionRate,
                focus7 == null ? 0 : focus7, focus30 == null ? 0 : focus30, totalSessions,
                Math.round(avgSession * 10) / 10.0,
                activeGoals, achievedGoals, Math.round(avgGoalProgress * 1000) / 1000.0,
                byQuadrant, byPriority, timeline, focusByHour, bestDay,
                avgMood == null ? null : Math.round(avgMood * 100) / 100.0,
                avgEnergy == null ? null : Math.round(avgEnergy * 100) / 100.0);
    }
}
