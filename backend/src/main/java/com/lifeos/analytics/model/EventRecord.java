package com.lifeos.analytics.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Raw archive of every event the projector consumed.
 *
 * Two jobs: it is the questions-we-have-not-thought-of-yet table, and its primary
 * key is the de-duplication guard — the projector refuses to move a counter for an
 * event id it has already stored.
 *
 * The payload stays untyped jsonb, exactly as it was as a Mongo sub-document, so a
 * new field on a producing event needs no migration here.
 */
@Entity
@Table(name = "event_record", schema = "analytics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventRecord {

    /** The producing event's id — makes redelivery a no-op. */
    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "aggregate_type", length = 48)
    private String aggregateType;

    @Column(name = "aggregate_id", length = 64)
    private String aggregateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}
