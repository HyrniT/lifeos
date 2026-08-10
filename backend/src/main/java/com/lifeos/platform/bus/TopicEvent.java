package com.lifeos.platform.bus;

import com.lifeos.common.event.DomainEvent;

/**
 * A domain event plus the topic it was published to.
 *
 * The topic is kept even though there is no broker any more: it is what tells a
 * handler whether an event is one of its concerns, and keeping it means the
 * handlers read exactly as they did when they were {@code @KafkaListener}s.
 */
public record TopicEvent(String topic, DomainEvent event) {
}
