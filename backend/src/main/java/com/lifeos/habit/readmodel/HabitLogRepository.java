package com.lifeos.habit.readmodel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HabitLogRepository extends JpaRepository<HabitLogView, UUID> {

    Optional<HabitLogView> findByHabitIdAndLogDate(UUID habitId, LocalDate logDate);

    List<HabitLogView> findByHabitIdAndLogDateBetweenOrderByLogDateAsc(UUID habitId, LocalDate from, LocalDate to);

    List<HabitLogView> findByUserIdAndLogDateBetweenOrderByLogDateAsc(UUID userId, LocalDate from, LocalDate to);

    List<HabitLogView> findByUserIdAndLogDate(UUID userId, LocalDate logDate);

    List<HabitLogView> findByHabitIdOrderByLogDateDesc(UUID habitId);

    void deleteByHabitId(UUID habitId);

    long countByHabitId(UUID habitId);

    /** Distinct days on which the user checked anything in — the cross-habit streak source. */
    @Query("SELECT DISTINCT l.logDate FROM HabitLogView l WHERE l.userId = :userId "
            + "AND l.logDate >= :since ORDER BY l.logDate DESC")
    List<LocalDate> activeDatesSince(@Param("userId") UUID userId, @Param("since") LocalDate since);

    @Query("SELECT l.logDate, COUNT(l) FROM HabitLogView l WHERE l.userId = :userId "
            + "AND l.logDate BETWEEN :from AND :to GROUP BY l.logDate ORDER BY l.logDate")
    List<Object[]> dailyCounts(@Param("userId") UUID userId,
                               @Param("from") LocalDate from,
                               @Param("to") LocalDate to);
}
