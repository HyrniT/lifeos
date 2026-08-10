package com.lifeos.analytics.repo;

import com.lifeos.analytics.model.EventRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EventRecordRepository extends JpaRepository<EventRecord, String> {

    List<EventRecord> findTop50ByUserIdOrderByOccurredAtDesc(UUID userId);

    List<EventRecord> findByUserIdAndOccurredAtAfterOrderByOccurredAtDesc(UUID userId, Instant since);

    long countByEventType(String eventType);

    /** Stands in for Mongo's TTL index; called from a nightly job. */
    @Modifying
    @Query("DELETE FROM EventRecord r WHERE r.occurredAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
