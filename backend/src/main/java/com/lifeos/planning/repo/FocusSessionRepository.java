package com.lifeos.planning.repo;

import com.lifeos.planning.domain.FocusSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FocusSessionRepository extends JpaRepository<FocusSession, UUID> {

    Optional<FocusSession> findByIdAndUserId(UUID id, UUID userId);

    List<FocusSession> findByUserIdAndSessionDateBetweenOrderByStartedAtAsc(
            UUID userId, LocalDate from, LocalDate to);

    /** At most one session should be open at a time; the newest wins if not. */
    @Query("SELECT s FROM FocusSession s WHERE s.userId = :userId AND s.endedAt IS NULL "
            + "ORDER BY s.startedAt DESC")
    List<FocusSession> openSessions(@Param("userId") UUID userId);

    @Query("""
            SELECT COALESCE(SUM(s.actualMinutes), 0) FROM FocusSession s
            WHERE s.userId = :userId AND s.completed = true
              AND s.sessionDate BETWEEN :from AND :to
            """)
    Long focusMinutes(@Param("userId") UUID userId,
                      @Param("from") LocalDate from,
                      @Param("to") LocalDate to);

    @Query("""
            SELECT s.sessionDate, COALESCE(SUM(s.actualMinutes), 0), COUNT(s) FROM FocusSession s
            WHERE s.userId = :userId AND s.completed = true AND s.sessionDate BETWEEN :from AND :to
            GROUP BY s.sessionDate ORDER BY s.sessionDate
            """)
    List<Object[]> dailyFocus(@Param("userId") UUID userId,
                              @Param("from") LocalDate from,
                              @Param("to") LocalDate to);

    long countByUserIdAndCompletedTrue(UUID userId);
}
