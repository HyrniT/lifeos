package com.lifeos.common.event;

import java.util.List;

/**
 * The seam between domain code and however events are delivered.
 *
 * Domain code has never known what is on the other side, which is why swapping a
 * broker for an in-process dispatcher touched no service class.
 *
 * @see com.lifeos.platform.bus.InProcessEventPublisher
 */
public interface EventPublisher {

    void publish(String topic, DomainEvent event);

    default void publishAll(String topic, List<DomainEvent> events) {
        events.forEach(e -> publish(topic, e));
    }
}
