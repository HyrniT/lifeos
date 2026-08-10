package com.lifeos.habit.eventstore;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StoredEventRepository extends JpaRepository<StoredEvent, UUID> {

    List<StoredEvent> findByAggregateIdOrderBySequenceNoAsc(UUID aggregateId);

    @Query("SELECT COALESCE(MAX(e.sequenceNo), 0) FROM StoredEvent e WHERE e.aggregateId = :aggregateId")
    long currentVersion(@Param("aggregateId") UUID aggregateId);

    @Query("""
            SELECT e FROM StoredEvent e
            WHERE e.userId = :userId AND e.occurredAt >= :since
            ORDER BY e.occurredAt DESC
            """)
    List<StoredEvent> recentForUser(@Param("userId") UUID userId, @Param("since") Instant since);

    /** Used by the projection rebuild path. */
    List<StoredEvent> findByUserIdOrderByOccurredAtAsc(UUID userId);

    long countByUserId(UUID userId);
}
