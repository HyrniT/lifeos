package com.lifeos.habit.eventstore;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Transactional outbox.
 *
 * Writing to Postgres and publishing to Kafka in one step is a dual write: either
 * can succeed while the other fails. Instead the event row and this row are written
 * in the same transaction, and a relay ships the outbox to Kafka afterwards. Worst
 * case a message is delivered twice, which consumers handle by being idempotent.
 */
@Entity
@Table(name = "event_outbox", schema = "habit", indexes = {
        @Index(name = "idx_outbox_unpublished", columnList = "published,created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 128)
    private String topic;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "sequence_no", nullable = false)
    private long sequenceNo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(nullable = false)
    @Builder.Default
    private boolean published = false;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "last_error", length = 512)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;
}
