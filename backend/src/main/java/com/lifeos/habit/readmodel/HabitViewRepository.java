package com.lifeos.habit.readmodel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HabitViewRepository extends JpaRepository<HabitView, UUID> {

    List<HabitView> findByUserIdAndArchivedOrderBySortOrderAscCreatedAtAsc(UUID userId, boolean archived);

    List<HabitView> findByUserIdOrderBySortOrderAscCreatedAtAsc(UUID userId);

    Optional<HabitView> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndArchivedFalse(UUID userId);

    @Query("SELECT COALESCE(MAX(h.sortOrder), -1) FROM HabitView h WHERE h.userId = :userId")
    int maxSortOrder(@Param("userId") UUID userId);

    /** Working set for the reminder scheduler: habits that asked to be reminded. */
    @Query("SELECT h FROM HabitView h WHERE h.archived = false AND h.reminderTime IS NOT NULL")
    List<HabitView> allWithReminders();

    /**
     * Working set for the streak-at-risk nudge, which applies to any live habit with
     * a streak worth protecting — not only the ones with a reminder time set.
     */
    @Query("SELECT h FROM HabitView h WHERE h.archived = false AND h.currentStreak >= :minStreak")
    List<HabitView> allWithStreakAtLeast(@Param("minStreak") int minStreak);
}
