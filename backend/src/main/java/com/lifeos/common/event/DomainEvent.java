package com.lifeos.common.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The shape of everything published on the event bus.
 *
 * The payload stays an untyped map on purpose: the handlers (analytics,
 * notifications) must keep working when a producer adds a field, and a typed class
 * per event would put a compile-time edge between every pair of packages.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DomainEvent(
        UUID eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        UUID userId,
        long sequence,
        Instant occurredAt,
        Map<String, Object> payload,
        Map<String, String> metadata
) {

    public static DomainEvent of(String eventType, String aggregateType, String aggregateId,
                                 UUID userId, long sequence, Map<String, Object> payload) {
        return new DomainEvent(UUID.randomUUID(), eventType, aggregateType, aggregateId,
                userId, sequence, Instant.now(), payload, Map.of());
    }

    /** Groups events by the thing they happened to; kept for the archive's sake. */
    public String partitionKey() {
        return aggregateId != null ? aggregateId : String.valueOf(userId);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object v = payload == null ? null : payload.get(key);
        if (v == null) {
            return null;
        }
        if (type == UUID.class && v instanceof String s) {
            return (T) UUID.fromString(s);
        }
        if (type == Long.class && v instanceof Number n) {
            return (T) Long.valueOf(n.longValue());
        }
        if (type == Integer.class && v instanceof Number n) {
            return (T) Integer.valueOf(n.intValue());
        }
        if (type == Double.class && v instanceof Number n) {
            return (T) Double.valueOf(n.doubleValue());
        }
        return type.isInstance(v) ? type.cast(v) : null;
    }
}
