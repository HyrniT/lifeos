package com.lifeos.habit.service;

import com.lifeos.common.exception.ApiException;
import com.lifeos.habit.dto.HabitDtos.*;
import com.lifeos.habit.readmodel.HabitLogRepository;
import com.lifeos.habit.readmodel.HabitLogView;
import com.lifeos.habit.readmodel.HabitView;
import com.lifeos.habit.readmodel.HabitViewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;

/**
 * The query side. Reads only the projections — never the event store — which is
 * what keeps these endpoints fast enough to drive a dashboard.
 */
@Service
public class HabitQueryService {

    private final HabitViewRepository habits;
    private final HabitLogRepository logs;
    private final StreakCalculator streaks;
    private final GamificationService gamification;

    public HabitQueryService(HabitViewRepository habits, HabitLogRepository logs,
                             StreakCalculator streaks, GamificationService gamification) {
        this.habits = habits;
        this.logs = logs;
        this.streaks = streaks;
        this.gamification = gamification;
    }

    @Transactional(readOnly = true)
    public List<HabitResponse> list(UUID userId, boolean includeArchived) {
        List<HabitView> views = includeArchived
                ? habits.findByUserIdOrderBySortOrderAscCreatedAtAsc(userId)
                : habits.findByUserIdAndArchivedOrderBySortOrderAscCreatedAtAsc(userId, false);

        Set<UUID> doneToday = new HashSet<>();
        logs.findByUserIdAndLogDate(userId, LocalDate.now())
                .forEach(l -> doneToday.add(l.getHabitId()));

        return views.stream().map(v -> HabitResponse.from(v, doneToday.contains(v.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public HabitResponse get(UUID userId, UUID habitId) {
        HabitView view = habits.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> ApiException.notFound("Habit", habitId));
        return HabitResponse.from(view, logs.findByHabitIdAndLogDate(habitId, LocalDate.now()).isPresent());
    }

    @Transactional(readOnly = true)
    public List<LogEntry> logsFor(UUID userId, UUID habitId, LocalDate from, LocalDate to) {
        habits.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> ApiException.notFound("Habit", habitId));
        return logs.findByHabitIdAndLogDateBetweenOrderByLogDateAsc(habitId, from, to)
                .stream().map(LogEntry::from).toList();
    }

    /** The dashboard's hero panel: what is due today and how far through it the user is. */
    @Transactional(readOnly = true)
    public TodaySummary today(UUID userId) {
        LocalDate today = LocalDate.now();
        List<HabitView> active = habits.findByUserIdAndArchivedOrderBySortOrderAscCreatedAtAsc(userId, false);

        Map<UUID, HabitLogView> todaysLogs = new HashMap<>();
        logs.findByUserIdAndLogDate(userId, today).forEach(l -> todaysLogs.put(l.getHabitId(), l));

        List<HabitView> due = active.stream().filter(h -> streaks.isDue(h, today)).toList();
        int completed = (int) due.stream().filter(h -> todaysLogs.containsKey(h.getId())).count();
        int xpToday = todaysLogs.values().stream().mapToInt(HabitLogView::getXpAwarded).sum();

        return new TodaySummary(
                today,
                due.size(),
                completed,
                due.isEmpty() ? 0.0 : Math.round((double) completed / due.size() * 1000) / 1000.0,
                xpToday,
                due.stream().map(h -> HabitResponse.from(h, todaysLogs.containsKey(h.getId()))).toList(),
                StatsResponse.from(gamification.statsFor(userId)));
    }

    /**
     * Contribution heatmap across every habit. Intensity is normalised against the
     * busiest day in the window so the scale adapts to how many habits a user tracks.
     */
    @Transactional(readOnly = true)
    public List<HeatmapCell> heatmap(UUID userId, LocalDate from, LocalDate to) {
        Map<LocalDate, Integer> counts = new HashMap<>();
        for (Object[] row : logs.dailyCounts(userId, from, to)) {
            counts.put((LocalDate) row[0], ((Number) row[1]).intValue());
        }
        int max = counts.values().stream().mapToInt(Integer::intValue).max().orElse(1);

        List<HeatmapCell> cells = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            int count = counts.getOrDefault(d, 0);
            cells.add(new HeatmapCell(d, count, max == 0 ? 0 : Math.round((double) count / max * 100) / 100.0));
        }
        return cells;
    }

    /** Per-habit deep dive: rates over three windows, weekday profile and trend. */
    @Transactional(readOnly = true)
    public HabitInsights insights(UUID userId, UUID habitId) {
        HabitView view = habits.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> ApiException.notFound("Habit", habitId));

        LocalDate today = LocalDate.now();
        List<HabitLogView> entries = logs.findByHabitIdOrderByLogDateDesc(habitId);
        Set<LocalDate> dates = new HashSet<>();
        entries.forEach(l -> dates.add(l.getLogDate()));

        double rate7 = streaks.completionRate(view, dates, today, 7);
        double rate30 = streaks.completionRate(view, dates, today, 30);
        double rate90 = streaks.completionRate(view, dates, today, 90);

        // Weekday profile: completions on each weekday divided by times it was due.
        Map<DayOfWeek, int[]> perWeekday = new EnumMap<>(DayOfWeek.class);
        LocalDate windowStart = today.minusDays(89);
        for (LocalDate d = windowStart; !d.isAfter(today); d = d.plusDays(1)) {
            if (!streaks.isDue(view, d)) {
                continue;
            }
            int[] pair = perWeekday.computeIfAbsent(d.getDayOfWeek(), k -> new int[2]);
            pair[1]++;
            if (dates.contains(d)) {
                pair[0]++;
            }
        }

        Map<String, Double> weekday = new LinkedHashMap<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            int[] pair = perWeekday.get(day);
            String label = day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            weekday.put(label, pair == null || pair[1] == 0
                    ? 0.0
                    : Math.round((double) pair[0] / pair[1] * 100) / 100.0);
        }

        String best = weekday.entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("—");
        String worst = weekday.entrySet().stream()
                .min(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("—");

        Double avgMood = entries.stream()
                .filter(l -> l.getMood() != null)
                .mapToInt(HabitLogView::getMood)
                .average().stream().boxed().findFirst().orElse(null);

        // Trend compares the last fortnight with the one before it; a 5-point gap is
        // the smallest change worth calling a direction rather than noise.
        double recent = streaks.completionRate(view, dates, today, 14);
        double previous = streaks.completionRate(view, dates, today.minusDays(14), 14);
        String trend = Math.abs(recent - previous) < 0.05 ? "steady" : (recent > previous ? "up" : "down");

        return new HabitInsights(habitId, view.getName(), view.getCurrentStreak(), view.getLongestStreak(),
                view.getTotalCheckIns(), rate7, rate30, rate90, weekday,
                heatmapFor(entries, today.minusDays(180), today),
                best, worst, avgMood == null ? null : Math.round(avgMood * 100) / 100.0, trend);
    }

    private List<HeatmapCell> heatmapFor(List<HabitLogView> entries, LocalDate from, LocalDate to) {
        Set<LocalDate> dates = new HashSet<>();
        entries.forEach(l -> dates.add(l.getLogDate()));
        List<HeatmapCell> cells = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            boolean done = dates.contains(d);
            cells.add(new HeatmapCell(d, done ? 1 : 0, done ? 1.0 : 0.0));
        }
        return cells;
    }
}
