package com.lifeos.habit.service;

import com.lifeos.habit.domain.HabitEnums.Frequency;
import com.lifeos.habit.readmodel.HabitView;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Streak and completion maths.
 *
 * "Streak" means different things per frequency, and getting this wrong is the
 * fastest way to lose a user's trust in a habit tracker:
 *  - DAILY counts consecutive days;
 *  - SPECIFIC_DAYS only counts the days the habit is actually scheduled, so
 *    skipping a Sunday on a Mon/Wed/Fri habit does not break anything;
 *  - INTERVAL allows the configured gap;
 *  - WEEKLY/MONTHLY_TARGET count consecutive periods that met the target.
 */
@Component
public class StreakCalculator {

    public record Streaks(int current, int longest) {
    }

    public Streaks compute(HabitView habit, List<LocalDate> checkedDatesDesc, LocalDate today) {
        if (checkedDatesDesc == null || checkedDatesDesc.isEmpty()) {
            return new Streaks(0, 0);
        }
        SortedSet<LocalDate> dates = new TreeSet<>(checkedDatesDesc);

        return switch (habit.getFrequency()) {
            case DAILY -> daily(dates, today, 1);
            case INTERVAL -> daily(dates, today, Math.max(1, habit.getIntervalDays() == null
                    ? 1 : habit.getIntervalDays()));
            case SPECIFIC_DAYS -> scheduledDays(dates, habit.getDaysOfWeek(), today);
            case WEEKLY_TARGET -> periodic(dates, today, habit.getTargetPerPeriod(), ChronoUnit.WEEKS);
            case MONTHLY_TARGET -> periodic(dates, today, habit.getTargetPerPeriod(), ChronoUnit.MONTHS);
        };
    }

    // ---- daily / interval -------------------------------------------------
    private Streaks daily(SortedSet<LocalDate> dates, LocalDate today, int allowedGap) {
        List<LocalDate> ordered = List.copyOf(dates);

        int longest = 1;
        int run = 1;
        for (int i = 1; i < ordered.size(); i++) {
            long gap = ChronoUnit.DAYS.between(ordered.get(i - 1), ordered.get(i));
            if (gap <= allowedGap) {
                run++;
                longest = Math.max(longest, run);
            } else {
                run = 1;
            }
        }

        // The current streak only survives if the most recent entry is still inside
        // the allowed gap from today — otherwise it is history, not a live streak.
        LocalDate last = ordered.get(ordered.size() - 1);
        if (ChronoUnit.DAYS.between(last, today) > allowedGap) {
            return new Streaks(0, longest);
        }

        int current = 1;
        for (int i = ordered.size() - 1; i > 0; i--) {
            long gap = ChronoUnit.DAYS.between(ordered.get(i - 1), ordered.get(i));
            if (gap <= allowedGap) {
                current++;
            } else {
                break;
            }
        }
        return new Streaks(current, Math.max(longest, current));
    }

    // ---- specific weekdays ------------------------------------------------
    private Streaks scheduledDays(SortedSet<LocalDate> dates, Set<Integer> daysOfWeek, LocalDate today) {
        if (daysOfWeek == null || daysOfWeek.isEmpty()) {
            return daily(dates, today, 1);
        }

        LocalDate earliest = dates.first();

        int longest = 0;
        int run = 0;
        // Walk every scheduled day from the first entry to today; a scheduled day
        // without an entry breaks the run.
        for (LocalDate d = earliest; !d.isAfter(today); d = d.plusDays(1)) {
            if (!isScheduled(d, daysOfWeek)) {
                continue;
            }
            if (dates.contains(d)) {
                run++;
                longest = Math.max(longest, run);
            } else if (d.isBefore(today)) {
                run = 0;
            }
        }

        // `run` already carries the right value whether or not today is done yet:
        // an unfinished scheduled today does not reset it (see the loop's guard).
        return new Streaks(run, Math.max(longest, run));
    }

    private boolean isScheduled(LocalDate date, Set<Integer> daysOfWeek) {
        return daysOfWeek.contains(date.getDayOfWeek().getValue());
    }

    // ---- weekly / monthly targets ----------------------------------------
    private Streaks periodic(SortedSet<LocalDate> dates, LocalDate today, int target, ChronoUnit unit) {
        int effectiveTarget = Math.max(1, target);

        int longest = 0;
        int run = 0;

        LocalDate cursor = startOfPeriod(dates.first(), unit);
        final LocalDate todayPeriod = startOfPeriod(today, unit);

        while (!cursor.isAfter(todayPeriod)) {
            final LocalDate periodStart = cursor;
            final LocalDate periodEnd = endOfPeriod(periodStart, unit);
            long count = dates.stream()
                    .filter(d -> !d.isBefore(periodStart) && !d.isAfter(periodEnd))
                    .count();

            if (count >= effectiveTarget) {
                run++;
                longest = Math.max(longest, run);
            } else if (periodStart.isBefore(todayPeriod)) {
                run = 0;              // a completed period that missed the target
            }
            cursor = unit == ChronoUnit.WEEKS ? cursor.plusWeeks(1) : cursor.plusMonths(1);
        }
        return new Streaks(run, Math.max(longest, run));
    }

    private LocalDate startOfPeriod(LocalDate date, ChronoUnit unit) {
        return unit == ChronoUnit.WEEKS
                ? date.with(DayOfWeek.MONDAY)
                : date.withDayOfMonth(1);
    }

    private LocalDate endOfPeriod(LocalDate start, ChronoUnit unit) {
        return unit == ChronoUnit.WEEKS
                ? start.plusDays(6)
                : start.withDayOfMonth(start.lengthOfMonth());
    }

    // ---- completion rate --------------------------------------------------
    /**
     * Fraction of the days the habit was actually due over the window that were
     * completed. Dividing by scheduled days rather than calendar days is what keeps
     * a three-days-a-week habit from looking like it is at 43%.
     */
    public double completionRate(HabitView habit, Set<LocalDate> checked, LocalDate today, int windowDays) {
        LocalDate from = today.minusDays(windowDays - 1L);
        LocalDate start = habit.getCreatedAt() == null ? from
                : maxDate(from, habit.getCreatedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate());

        int due = 0;
        int done = 0;
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            if (!isDue(habit, d)) {
                continue;
            }
            due++;
            if (checked.contains(d)) {
                done++;
            }
        }
        return due == 0 ? 0.0 : Math.round((double) done / due * 1000) / 1000.0;
    }

    /** Whether the habit is scheduled on the given date. */
    public boolean isDue(HabitView habit, LocalDate date) {
        Frequency frequency = habit.getFrequency();
        return switch (frequency) {
            case DAILY, WEEKLY_TARGET, MONTHLY_TARGET -> true;
            case SPECIFIC_DAYS -> habit.getDaysOfWeek() != null
                    && habit.getDaysOfWeek().contains(date.getDayOfWeek().getValue());
            case INTERVAL -> {
                LocalDate last = habit.getLastCheckInDate();
                int interval = habit.getIntervalDays() == null ? 1 : Math.max(1, habit.getIntervalDays());
                yield last == null || !date.isBefore(last.plusDays(interval));
            }
        };
    }

    private static LocalDate maxDate(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}
