package com.lifeos.habit.eventstore;

import com.lifeos.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Append-only store with optimistic concurrency.
 *
 * A command loads an aggregate at version N, decides, and appends expecting the
 * store to still be at N. If another request got there first the unique constraint
 * on (aggregate_id, sequence_no) rejects the insert and the caller gets a 409
 * rather than silently losing one of the two writes.
 */
@Service
public class EventStore {

    private static final Logger log = LoggerFactory.getLogger(EventStore.class);

    private final StoredEventRepository events;
    private final OutboxRepository outbox;

    public EventStore(StoredEventRepository events, OutboxRepository outbox) {
        this.events = events;
        this.outbox = outbox;
    }

    public List<StoredEvent> load(UUID aggregateId) {
        return events.findByAggregateIdOrderBySequenceNoAsc(aggregateId);
    }

    public long currentVersion(UUID aggregateId) {
        return events.currentVersion(aggregateId);
    }

    /**
     * Appends {@code pending} atomically with their outbox rows.
     *
     * @param expectedVersion the version the caller read; {@code -1} skips the check
     *                        (used when creating a brand-new aggregate).
     */
    @Transactional
    public List<StoredEvent> append(UUID aggregateId, String aggregateType, UUID userId,
                                    long expectedVersion, String topic,
                                    List<PendingEvent> pending) {
        if (pending.isEmpty()) {
            return List.of();
        }

        long actual = currentVersion(aggregateId);
        if (expectedVersion >= 0 && actual != expectedVersion) {
            throw ApiException.concurrency(aggregateType, expectedVersion, actual);
        }

        Instant now = Instant.now();

        try {
            List<StoredEvent> saved = new java.util.ArrayList<>(pending.size());
            long sequence = actual;

            for (PendingEvent p : pending) {
                sequence++;
                saved.add(events.save(StoredEvent.builder()
                        .aggregateId(aggregateId)
                        .aggregateType(aggregateType)
                        .sequenceNo(sequence)
                        .eventType(p.eventType())
                        .userId(userId)
                        .payload(p.payload())
                        .occurredAt(now)
                        .build()));

                outbox.save(OutboxEvent.builder()
                        .topic(topic)
                        .eventType(p.eventType())
                        .aggregateId(aggregateId)
                        .userId(userId)
                        .sequenceNo(sequence)
                        .payload(p.payload())
                        .createdAt(now)
                        .build());
            }

            log.debug("Appended {} event(s) to {} {} (version {} -> {})",
                    saved.size(), aggregateType, aggregateId, actual, sequence);
            return saved;

        } catch (DataIntegrityViolationException ex) {
            // Another transaction claimed the same sequence between our read and write.
            throw ApiException.concurrency(aggregateType, expectedVersion, currentVersion(aggregateId));
        }
    }

    /** An event decided by an aggregate but not yet persisted. */
    public record PendingEvent(String eventType, java.util.Map<String, Object> payload) {
    }
}
