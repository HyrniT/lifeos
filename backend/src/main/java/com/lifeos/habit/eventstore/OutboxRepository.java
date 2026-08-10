package com.lifeos.habit.eventstore;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("SELECT o FROM OutboxEvent o WHERE o.published = false AND o.attempts < :maxAttempts "
            + "ORDER BY o.createdAt ASC")
    List<OutboxEvent> findPending(@Param("maxAttempts") int maxAttempts, Pageable pageable);

    @Modifying
    @Query("DELETE FROM OutboxEvent o WHERE o.published = true AND o.publishedAt < :cutoff")
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);

    long countByPublishedFalse();
}
