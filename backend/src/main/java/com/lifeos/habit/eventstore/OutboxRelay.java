package com.lifeos.habit.eventstore;

import com.lifeos.common.event.DomainEvent;
import com.lifeos.common.event.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Ships outbox rows onto the event bus.
 *
 * The outbox survived the merge, and it earns its keep more than it did before.
 * Writing the event row and dispatching it were once a dual write across a
 * network; now they would be a single transaction — but a handler that fails still
 * needs somewhere to be retried from, and with no broker to redeliver, that
 * somewhere is this table.
 *
 * Runs on a short fixed delay rather than a cron so a check-in shows up in the
 * charts within a second; rows that keep failing stop being retried after
 * {@code maxAttempts} and stay in the table for an operator to inspect.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outbox;
    private final EventPublisher publisher;
    private final int batchSize;
    private final int maxAttempts;

    public OutboxRelay(OutboxRepository outbox, EventPublisher publisher,
                       @Value("${lifeos.outbox.batch-size:100}") int batchSize,
                       @Value("${lifeos.outbox.max-attempts:8}") int maxAttempts) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${lifeos.outbox.poll-ms:1000}")
    @Transactional
    public void relay() {
        List<OutboxEvent> pending = outbox.findPending(maxAttempts, PageRequest.of(0, batchSize));
        if (pending.isEmpty()) {
            return;
        }

        for (OutboxEvent row : pending) {
            try {
                publisher.publish(row.getTopic(), new DomainEvent(
                        row.getId(),
                        row.getEventType(),
                        "Habit",
                        row.getAggregateId().toString(),
                        row.getUserId(),
                        row.getSequenceNo(),
                        row.getCreatedAt(),
                        row.getPayload(),
                        java.util.Map.of("source", "habit")));

                row.setPublished(true);
                row.setPublishedAt(Instant.now());
                row.setLastError(null);
            } catch (Exception ex) {
                row.setAttempts(row.getAttempts() + 1);
                row.setLastError(truncate(ex.getMessage()));
                log.warn("Outbox row {} failed on attempt {}: {}", row.getId(), row.getAttempts(), ex.getMessage());
            }
            outbox.save(row);
        }

        log.debug("Relayed {} outbox row(s)", pending.size());
    }

    /** Published rows are evidence, not history — the event store already has that. */
    @Scheduled(cron = "${lifeos.outbox.cleanup-cron:0 30 3 * * *}")
    @Transactional
    public void cleanup() {
        int removed = outbox.deletePublishedBefore(Instant.now().minus(java.time.Duration.ofDays(3)));
        if (removed > 0) {
            log.info("Removed {} published outbox row(s)", removed);
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 512 ? value : value.substring(0, 512);
    }
}
