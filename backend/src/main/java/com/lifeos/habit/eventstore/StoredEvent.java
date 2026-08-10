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
 * One immutable row per fact that has happened to a habit.
 *
 * This table is the system of record — the habit tables elsewhere in this service
 * are projections that can be dropped and rebuilt from here at any time.
 */
@Entity
@Table(name = "event_store", schema = "habit", uniqueConstraints = {
        // The concurrency guard: two writers cannot both claim sequence N.
        @UniqueConstraint(name = "uk_event_aggregate_sequence", columnNames = {"aggregate_id", "sequence_no"})
}, indexes = {
        @Index(name = "idx_event_aggregate", columnList = "aggregate_id,sequence_no"),
        @Index(name = "idx_event_user_time", columnList = "user_id,occurred_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoredEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "aggregate_type", nullable = false, length = 48)
    private String aggregateType;

    @Column(name = "sequence_no", nullable = false)
    private long sequenceNo;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Stored as jsonb so events stay queryable without a migration per field. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
