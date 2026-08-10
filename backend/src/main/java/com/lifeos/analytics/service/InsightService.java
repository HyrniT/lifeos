package com.lifeos.analytics.service;

import com.lifeos.analytics.model.DailyRollup;
import com.lifeos.analytics.repo.DailyRollupRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;

/**
 * The cross-domain layer — the part no single service can answer on its own.
 *
 * Everything here is correlational, and the wording says so. "Days you hit your
 * habits, you spend 22% less" is a real pattern in the user's own data; claiming it
 * is a cause would be dishonest, and users notice.
 */
@Service
public class InsightService {

    /** Below this many overlapping days a correlation is noise, so we stay quiet. */
    private static final int MIN_SAMPLE_DAYS = 14;

    private final DailyRollupRepository rollups;

    public InsightService(DailyRollupRepository rollups) {
        this.rollups = rollups;
    }

    public record Correlation(
            String code, String title, String message, double strength,
            int sampleDays, String direction, Map<String, Object> data
    ) {
    }

    public record TimelinePoint(
            LocalDate date, int habitCheckIns, int xpEarned, BigDecimal expense, BigDecimal income,
            int tasksCompleted, int focusMinutes, Integer mood
    ) {
    }

    public record LifeOverview(
            LocalDate from,
            LocalDate to,
            int activeDays,
            int totalCheckIns,
            int totalXp,
            BigDecimal totalSpent,
            BigDecimal totalEarned,
            int totalTasksCompleted,
            int totalFocusMinutes,
            double habitConsistency,
            double averageDailySpend,
            String strongestDay,
            String weakestDay,
            List<TimelinePoint> timeline,
            List<Correlation> correlations,
            Map<String, Double> balanceScore
    ) {
    }

    public LifeOverview overview(UUID userId, LocalDate from, LocalDate to) {
        List<DailyRollup> data = rollups.findInRange(userId, from, to);

        Map<LocalDate, DailyRollup> byDate = new HashMap<>();
        data.forEach(r -> byDate.put(r.getDate(), r));

        List<TimelinePoint> timeline = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            DailyRollup r = byDate.get(d);
            timeline.add(r == null
                    ? new TimelinePoint(d, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, null)
                    : new TimelinePoint(d, r.getHabitCheckIns(), r.getXpEarned(), r.getExpenseTotal(),
                            r.getIncomeTotal(), r.getTasksCompleted(), r.getFocusMinutes(), r.getMood()));
        }

        int totalDays = timeline.size();
        int activeDays = (int) data.stream().filter(r -> r.getHabitCheckIns() > 0).count();
        int checkIns = data.stream().mapToInt(DailyRollup::getHabitCheckIns).sum();
        int xp = data.stream().mapToInt(DailyRollup::getXpEarned).sum();
        int tasks = data.stream().mapToInt(DailyRollup::getTasksCompleted).sum();
        int focus = data.stream().mapToInt(DailyRollup::getFocusMinutes).sum();
        BigDecimal spent = data.stream().map(DailyRollup::getExpenseTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal earned = data.stream().map(DailyRollup::getIncomeTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<DayOfWeek, int[]> perWeekday = new EnumMap<>(DayOfWeek.class);
        timeline.forEach(p -> {
            int[] pair = perWeekday.computeIfAbsent(p.date().getDayOfWeek(), k -> new int[2]);
            pair[0] += p.habitCheckIns() + p.tasksCompleted();
            pair[1]++;
        });
        String strongest = extremeWeekday(perWeekday, true);
        String weakest = extremeWeekday(perWeekday, false);

        return new LifeOverview(
                from, to, activeDays, checkIns, xp,
                spent.setScale(2, RoundingMode.HALF_UP),
                earned.setScale(2, RoundingMode.HALF_UP),
                tasks, focus,
                totalDays == 0 ? 0 : Math.round((double) activeDays / totalDays * 1000) / 1000.0,
                totalDays == 0 ? 0 : spent.divide(BigDecimal.valueOf(totalDays), 2, RoundingMode.HALF_UP)
                        .doubleValue(),
                strongest, weakest, timeline,
                correlations(data), balanceScore(data));
    }

    // ========================================================== correlations
    private List<Correlation> correlations(List<DailyRollup> data) {
        List<Correlation> found = new ArrayList<>();
        if (data.size() < MIN_SAMPLE_DAYS) {
            return found;
        }

        // Habit consistency vs spending.
        double[] checkIns = data.stream().mapToDouble(DailyRollup::getHabitCheckIns).toArray();
        double[] spend = data.stream().mapToDouble(r -> r.getExpenseTotal().doubleValue()).toArray();
        double rHabitSpend = pearson(checkIns, spend);
        if (Math.abs(rHabitSpend) >= 0.3) {
            boolean less = rHabitSpend < 0;
            found.add(new Correlation("HABIT_SPEND",
                    less ? "Consistent days cost less" : "Busy days cost more",
                    less
                            ? "On days you complete more habits, you tend to spend less. This is a pattern in your data, not proof of cause."
                            : "Days with more habit check-ins also show higher spending — worth a look at what those days have in common.",
                    round(rHabitSpend), data.size(), less ? "negative" : "positive",
                    Map.of("coefficient", round(rHabitSpend))));
        }

        // Focus time vs tasks completed.
        double[] focus = data.stream().mapToDouble(DailyRollup::getFocusMinutes).toArray();
        double[] tasks = data.stream().mapToDouble(DailyRollup::getTasksCompleted).toArray();
        double rFocusTasks = pearson(focus, tasks);
        if (rFocusTasks >= 0.35) {
            found.add(new Correlation("FOCUS_OUTPUT", "Focus time tracks your output",
                    "Days with more focused minutes are also the days you finish more tasks.",
                    round(rFocusTasks), data.size(), "positive",
                    Map.of("coefficient", round(rFocusTasks))));
        }

        // Mood vs habit completion — only over days where mood was actually recorded.
        List<DailyRollup> withMood = data.stream().filter(r -> r.getMood() != null).toList();
        if (withMood.size() >= MIN_SAMPLE_DAYS) {
            double[] mood = withMood.stream().mapToDouble(DailyRollup::getMood).toArray();
            double[] moodCheckIns = withMood.stream().mapToDouble(DailyRollup::getHabitCheckIns).toArray();
            double rMood = pearson(mood, moodCheckIns);
            if (Math.abs(rMood) >= 0.3) {
                found.add(new Correlation("MOOD_HABITS", "Mood moves with your habits",
                        rMood > 0
                                ? "You log a better mood on days you complete more habits."
                                : "You log a lower mood on days with more habit check-ins — that may be a sign of overloading yourself.",
                        round(rMood), withMood.size(), rMood > 0 ? "positive" : "negative",
                        Map.of("coefficient", round(rMood))));
            }
        }

        return found;
    }

    /** Pearson's r. Returns 0 when either series is flat, which is the honest answer. */
    static double pearson(double[] x, double[] y) {
        int n = Math.min(x.length, y.length);
        if (n < 2) {
            return 0;
        }
        double meanX = Arrays.stream(x, 0, n).average().orElse(0);
        double meanY = Arrays.stream(y, 0, n).average().orElse(0);

        double num = 0;
        double denX = 0;
        double denY = 0;
        for (int i = 0; i < n; i++) {
            double dx = x[i] - meanX;
            double dy = y[i] - meanY;
            num += dx * dy;
            denX += dx * dx;
            denY += dy * dy;
        }
        if (denX == 0 || denY == 0) {
            return 0;
        }
        return num / Math.sqrt(denX * denY);
    }

    /** A 0-1 score per life area so the radar chart has something meaningful to plot. */
    private Map<String, Double> balanceScore(List<DailyRollup> data) {
        if (data.isEmpty()) {
            return Map.of("habits", 0.0, "money", 0.0, "productivity", 0.0,
                    "focus", 0.0, "wellbeing", 0.0);
        }
        int days = data.size();

        double habits = normalise(data.stream().mapToInt(DailyRollup::getHabitCheckIns).sum() / (double) days, 3);
        double productivity = normalise(
                data.stream().mapToInt(DailyRollup::getTasksCompleted).sum() / (double) days, 4);
        double focusScore = normalise(
                data.stream().mapToInt(DailyRollup::getFocusMinutes).sum() / (double) days, 120);

        BigDecimal income = data.stream().map(DailyRollup::getIncomeTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expense = data.stream().map(DailyRollup::getExpenseTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        double money = income.compareTo(BigDecimal.ZERO) <= 0
                ? (expense.compareTo(BigDecimal.ZERO) == 0 ? 0.5 : 0.2)
                : Math.max(0, Math.min(1, income.subtract(expense)
                        .divide(income, 4, RoundingMode.HALF_UP).doubleValue() * 2.5));

        double wellbeing = data.stream().filter(r -> r.getMood() != null)
                .mapToInt(DailyRollup::getMood).average()
                .stream().map(m -> (m - 1) / 4.0).findFirst().orElse(0.5);

        Map<String, Double> scores = new LinkedHashMap<>();
        scores.put("habits", round(habits));
        scores.put("money", round(money));
        scores.put("productivity", round(productivity));
        scores.put("focus", round(focusScore));
        scores.put("wellbeing", round(wellbeing));
        return scores;
    }

    private static double normalise(double value, double target) {
        return Math.max(0, Math.min(1, value / target));
    }

    private static String extremeWeekday(Map<DayOfWeek, int[]> perWeekday, boolean max) {
        return perWeekday.entrySet().stream()
                .filter(e -> e.getValue()[1] > 0)
                .sorted((a, b) -> {
                    double avgA = (double) a.getValue()[0] / a.getValue()[1];
                    double avgB = (double) b.getValue()[0] / b.getValue()[1];
                    return max ? Double.compare(avgB, avgA) : Double.compare(avgA, avgB);
                })
                .map(e -> e.getKey().getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .findFirst().orElse("—");
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
