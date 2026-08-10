package com.lifeos.analytics.repo;

import com.lifeos.analytics.model.DailyRollup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyRollupRepository extends JpaRepository<DailyRollup, String> {

    Optional<DailyRollup> findByUserIdAndDate(UUID userId, LocalDate date);

    /**
     * Inclusive on both ends and ordered by date.
     *
     * Spelled out rather than derived: {@code findByUserIdAndDateBetween} is
     * inclusive in JPA but was exclusive in the Mongo edition this replaced, and a
     * silent off-by-one that drops today from every "last N days" chart is not
     * something a reader should have to know a keyword's semantics to rule out.
     */
    @Query("""
            SELECT r FROM DailyRollup r
            WHERE r.userId = :userId AND r.date >= :from AND r.date <= :to
            ORDER BY r.date ASC
            """)
    List<DailyRollup> findInRange(@Param("userId") UUID userId,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to);

    long countByUserId(UUID userId);
}
