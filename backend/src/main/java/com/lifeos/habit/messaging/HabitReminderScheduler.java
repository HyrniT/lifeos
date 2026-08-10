package com.lifeos.habit.messaging;

import com.lifeos.common.event.ReminderMessage;
import com.lifeos.habit.domain.UserSettings;
import com.lifeos.habit.readmodel.HabitLogRepository;
import com.lifeos.habit.readmodel.HabitView;
import com.lifeos.habit.readmodel.HabitViewRepository;
import com.lifeos.habit.readmodel.HabitUserSettingsRepository;
import com.lifeos.habit.service.StreakCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.lifeos.platform.bus.ReminderBus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sends the habit reminders the app has always let users configure.
 *
 * Two nudges, and the second is the one people actually thank you for:
 *
 *  - **At the chosen time**, if the habit is due today and not yet done.
 *  - **Late in the evening**, if a streak worth protecting is about to break.
 *    Losing a 40-day streak to forgetfulness is the single most demoralising thing
 *    a habit tracker can let happen, and a 21:00 nudge prevents most of it.
 *
 * Both are suppressed the moment the habit is checked in, and both are keyed by
 * date so a restart cannot produce a second one.
 */
@Component
public class HabitReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(HabitReminderScheduler.class);

    private final HabitViewRepository habits;
    private final HabitLogRepository logs;
    private final HabitUserSettingsRepository userSettings;
    private final StreakCalculator streaks;
    private final ReminderBus reminders;

    private final Duration catchUp;
    private final LocalTime streakRiskHour;
    private final int streakRiskThreshold;

    public HabitReminderScheduler(
            HabitViewRepository habits, HabitLogRepository logs,
            HabitUserSettingsRepository userSettings, StreakCalculator streaks,
            ReminderBus reminders,
            @Value("${lifeos.reminders.catch-up-hours:2}") long catchUpHours,
            @Value("${lifeos.reminders.streak-risk-hour:21:00}") String streakRiskHour,
            @Value("${lifeos.reminders.streak-risk-threshold:3}") int streakRiskThreshold) {
        this.habits = habits;
        this.logs = logs;
        this.userSettings = userSettings;
        this.streaks = streaks;
        this.reminders = reminders;
        this.catchUp = Duration.ofHours(catchUpHours);
        this.streakRiskHour = LocalTime.parse(streakRiskHour);
        this.streakRiskThreshold = streakRiskThreshold;
    }

    @Scheduled(fixedDelayString = "${lifeos.reminders.habit-interval-ms:300000}")
    @Transactional(readOnly = true)
    public void scanHabitReminders() {
        Instant now = Instant.now();
        Map<UUID, ZoneId> zones = new HashMap<>();
        int emitted = 0;

        for (HabitView habit : habits.allWithReminders()) {
            ZoneId zone = zones.computeIfAbsent(habit.getUserId(), this::zoneFor);
            LocalDate today = now.atZone(zone).toLocalDate();

            if (!streaks.isDue(habit, today) || isDone(habit, today)) {
                continue;
            }

            Instant fireAt = today.atTime(habit.getReminderTime()).atZone(zone).toInstant();
            if (!isDue(fireAt, now)) {
                continue;
            }

            publish(ReminderMessage.of(habit.getUserId(), ReminderMessage.Kind.HABIT_DUE,
                            "habit:%s:%s".formatted(habit.getId(), today))
                    .title(habit.getName())
                    .body(describeTarget(habit)
                            + (habit.getCurrentStreak() > 0
                                    ? " — keep the %d-day streak going.".formatted(habit.getCurrentStreak())
                                    : ""))
                    .icon(habit.getIcon())
                    .severity(ReminderMessage.Severity.INFO)
                    .deepLink("/habits?habit=" + habit.getId())
                    .data(Map.of("habitId", habit.getId().toString(),
                            "streak", habit.getCurrentStreak()))
                    .build());
            emitted++;
        }

        emitted += scanStreaksAtRisk(now, zones);

        if (emitted > 0) {
            log.info("Emitted {} habit reminder(s)", emitted);
        }
    }

    /**
     * The evening save. Only fires for streaks long enough to be worth protecting —
     * nudging someone about a one-day streak is noise, not help.
     */
    private int scanStreaksAtRisk(Instant now, Map<UUID, ZoneId> zones) {
        int emitted = 0;
        List<HabitView> candidates = habits.allWithStreakAtLeast(streakRiskThreshold);

        for (HabitView habit : candidates) {
            ZoneId zone = zones.computeIfAbsent(habit.getUserId(), this::zoneFor);
            LocalDate today = now.atZone(zone).toLocalDate();

            if (!streaks.isDue(habit, today) || isDone(habit, today)) {
                continue;
            }

            Instant fireAt = today.atTime(streakRiskHour).atZone(zone).toInstant();
            if (!isDue(fireAt, now)) {
                continue;
            }

            publish(ReminderMessage.of(habit.getUserId(), ReminderMessage.Kind.HABIT_STREAK_AT_RISK,
                            "habit:%s:risk:%s".formatted(habit.getId(), today))
                    .title("%d-day streak at risk".formatted(habit.getCurrentStreak()))
                    .body("\"%s\" is still open today.".formatted(habit.getName()))
                    .icon("flame")
                    .severity(ReminderMessage.Severity.WARNING)
                    .deepLink("/habits?habit=" + habit.getId())
                    // Worth interrupting for: by definition there are only hours left.
                    .urgent(true)
                    .data(Map.of("habitId", habit.getId().toString(),
                            "streak", habit.getCurrentStreak()))
                    .build());
            emitted++;
        }
        return emitted;
    }

    // ================================================================ helpers
    private boolean isDone(HabitView habit, LocalDate date) {
        return logs.findByHabitIdAndLogDate(habit.getId(), date).isPresent();
    }

    private String describeTarget(HabitView habit) {
        if (habit.getTargetValue() == null || habit.getTargetValue() <= 1) {
            return "Time to check this one off.";
        }
        String unit = habit.getUnitLabel() != null && !habit.getUnitLabel().isBlank()
                ? habit.getUnitLabel()
                : habit.getUnit().name().toLowerCase();
        return "Target: %s %s.".formatted(trimNumber(habit.getTargetValue()), unit);
    }

    private static String trimNumber(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /**
     * True once the moment has passed but not by more than the catch-up window, so
     * a restart does not swallow the day's reminders. Re-emitting is harmless: the
     * dedupe key is enforced downstream.
     */
    private boolean isDue(Instant fireAt, Instant now) {
        return !fireAt.isAfter(now) && fireAt.isAfter(now.minus(catchUp));
    }

    private ZoneId zoneFor(UUID userId) {
        return userSettings.findById(userId).map(UserSettings::zone).orElse(ZoneOffset.UTC);
    }

    private void publish(ReminderMessage message) {
        reminders.sendReminder(message);
    }
}
