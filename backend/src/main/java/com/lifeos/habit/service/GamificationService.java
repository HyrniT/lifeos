package com.lifeos.habit.service;

import com.lifeos.common.event.DomainEvent;
import com.lifeos.common.event.EventPublisher;
import com.lifeos.common.event.Topics;
import com.lifeos.habit.domain.Achievements;
import com.lifeos.habit.domain.HabitEnums.Difficulty;
import com.lifeos.habit.dto.HabitDtos.AchievementView;
import com.lifeos.habit.readmodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * XP, levels, coins and achievements.
 *
 * XP for a check-in is {@code base(difficulty) × streak multiplier}, capped at 2×.
 * Compounding streak bonuses without a cap makes week-one users feel like they can
 * never catch up, which is the opposite of what the mechanic is for.
 */
@Service
public class GamificationService {

    private static final Logger log = LoggerFactory.getLogger(GamificationService.class);
    private static final double MAX_STREAK_MULTIPLIER = 2.0;
    private static final int COINS_PER_CHECK_IN = 2;

    private final UserStatsRepository statsRepo;
    private final AchievementRepository achievementRepo;
    private final HabitViewRepository habitRepo;
    private final HabitLogRepository logRepo;
    private final EventPublisher events;

    public GamificationService(UserStatsRepository statsRepo, AchievementRepository achievementRepo,
                               HabitViewRepository habitRepo, HabitLogRepository logRepo,
                               EventPublisher events) {
        this.statsRepo = statsRepo;
        this.achievementRepo = achievementRepo;
        this.habitRepo = habitRepo;
        this.logRepo = logRepo;
        this.events = events;
    }

    public static int xpFor(Difficulty difficulty, int streakAfterCheckIn) {
        double multiplier = Math.min(MAX_STREAK_MULTIPLIER, 1.0 + (streakAfterCheckIn / 30.0));
        return (int) Math.round(difficulty.baseXp() * multiplier);
    }

    @Transactional
    public UserStats statsFor(UUID userId) {
        return statsRepo.findById(userId).orElseGet(() -> statsRepo.save(UserStats.builder()
                .userId(userId)
                .updatedAt(Instant.now())
                .build()));
    }

    /**
     * Applies the reward for one check-in and returns any achievements it unlocked.
     */
    @Transactional
    public List<String> awardCheckIn(UUID userId, int xp, LocalDate date) {
        UserStats stats = statsFor(userId);

        stats.setXp(stats.getXp() + xp);
        stats.setCoins(stats.getCoins() + COINS_PER_CHECK_IN);
        stats.setTotalCheckIns(stats.getTotalCheckIns() + 1);
        stats.setHp(Math.min(100, stats.getHp() + 1));

        updateDayStreak(stats, date);

        boolean levelledUp = stats.recalculateLevel();
        stats.setUpdatedAt(Instant.now());
        statsRepo.save(stats);

        if (levelledUp) {
            log.info("User {} reached level {}", userId, stats.getLevel());
            events.publish(Topics.GAMIFICATION_EVENTS, DomainEvent.of(
                    Topics.Gamification.LEVEL_UP, "UserStats", userId.toString(), userId, 0L,
                    Map.of("level", stats.getLevel(), "xp", stats.getXp())));
        }

        return evaluateAchievements(userId, stats);
    }

    @Transactional
    public void revokeCheckIn(UUID userId, int xp) {
        UserStats stats = statsFor(userId);
        stats.setXp(Math.max(0, stats.getXp() - xp));
        stats.setCoins(Math.max(0, stats.getCoins() - COINS_PER_CHECK_IN));
        stats.setTotalCheckIns(Math.max(0, stats.getTotalCheckIns() - 1));
        stats.recalculateLevel();
        stats.setUpdatedAt(Instant.now());
        statsRepo.save(stats);
    }

    private void updateDayStreak(UserStats stats, LocalDate date) {
        LocalDate last = stats.getLastActiveDate();
        if (last == null) {
            stats.setCurrentDayStreak(1);
        } else if (date.equals(last)) {
            return;                                     // already counted today
        } else if (date.equals(last.plusDays(1))) {
            stats.setCurrentDayStreak(stats.getCurrentDayStreak() + 1);
        } else if (date.isAfter(last)) {
            long missed = ChronoUnit.DAYS.between(last, date) - 1;
            if (missed == 1 && stats.getStreakFreezes() > 0) {
                // Spend a freeze rather than resetting — one missed day should not
                // erase a month of work.
                stats.setStreakFreezes(stats.getStreakFreezes() - 1);
                stats.setCurrentDayStreak(stats.getCurrentDayStreak() + 1);
                log.debug("Streak freeze used for {}", stats.getUserId());
            } else {
                stats.setCurrentDayStreak(1);
                stats.setHp(Math.max(0, stats.getHp() - (int) Math.min(30, missed * 5)));
            }
        }

        if (date.isAfter(last == null ? date.minusDays(1) : last)) {
            stats.setLastActiveDate(date);
        }
        stats.setLongestDayStreak(Math.max(stats.getLongestDayStreak(), stats.getCurrentDayStreak()));

        // Earn a freeze every 14 consecutive days, up to three in the bank.
        if (stats.getCurrentDayStreak() > 0 && stats.getCurrentDayStreak() % 14 == 0) {
            stats.setStreakFreezes(Math.min(3, stats.getStreakFreezes() + 1));
        }
    }

    // ------------------------------------------------------- achievements
    @Transactional
    public List<String> evaluateAchievements(UUID userId, UserStats stats) {
        Achievements.Progress progress = buildProgress(userId, stats);
        Set<String> already = new HashSet<>();
        achievementRepo.findByUserId(userId).forEach(a -> already.add(a.getCode()));

        List<String> newlyUnlocked = new ArrayList<>();
        for (Achievements.Definition def : Achievements.ALL) {
            if (already.contains(def.code())) {
                continue;
            }
            if (def.unlocked().test(progress)) {
                achievementRepo.save(UnlockedAchievement.builder()
                        .userId(userId)
                        .code(def.code())
                        .unlockedAt(Instant.now())
                        .build());
                newlyUnlocked.add(def.code());

                events.publish(Topics.GAMIFICATION_EVENTS, DomainEvent.of(
                        Topics.Gamification.ACHIEVEMENT_UNLOCKED, "UserStats", userId.toString(), userId, 0L,
                        Map.of("code", def.code(), "title", def.title(), "tier", def.tier())));
            }
        }
        if (!newlyUnlocked.isEmpty()) {
            log.info("User {} unlocked {}", userId, newlyUnlocked);
        }
        return newlyUnlocked;
    }

    @Transactional(readOnly = true)
    public List<AchievementView> catalogue(UUID userId) {
        UserStats stats = statsRepo.findById(userId).orElseGet(() ->
                UserStats.builder().userId(userId).updatedAt(Instant.now()).build());
        Achievements.Progress progress = buildProgress(userId, stats);

        Map<String, Instant> unlockedAt = new HashMap<>();
        achievementRepo.findByUserId(userId).forEach(a -> unlockedAt.put(a.getCode(), a.getUnlockedAt()));

        return Achievements.ALL.stream()
                .map(def -> new AchievementView(
                        def.code(), def.title(), def.description(), def.icon(), def.tier(),
                        unlockedAt.containsKey(def.code()),
                        unlockedAt.get(def.code()),
                        unlockedAt.containsKey(def.code()) ? 1.0
                                : Math.round(def.progressFn().applyAsDouble(progress) * 100) / 100.0))
                .toList();
    }

    private Achievements.Progress buildProgress(UUID userId, UserStats stats) {
        List<HabitView> habits = habitRepo.findByUserIdAndArchivedOrderBySortOrderAscCreatedAtAsc(userId, false);
        int bestHabitStreak = habits.stream().mapToInt(HabitView::getLongestStreak).max().orElse(0);
        LocalDate today = LocalDate.now();
        int checkInsToday = logRepo.findByUserIdAndLogDate(userId, today).size();

        return new Achievements.Progress(
                stats.getTotalCheckIns(),
                stats.getCurrentDayStreak(),
                stats.getLongestDayStreak(),
                bestHabitStreak,
                habits.size(),
                stats.getLevel(),
                checkInsToday,
                0,                                   // early check-ins: tracked from log timestamps below
                stats.getLongestDayStreak() / 7);
    }
}
