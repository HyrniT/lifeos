package com.lifeos.platform.bus;

import com.lifeos.common.event.DomainEvent;
import com.lifeos.common.event.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * What replaced Kafka.
 *
 * Domain code still calls {@link EventPublisher#publish}; the event is handed to
 * Spring's application context instead of a broker, and the handlers that used to
 * be {@code @KafkaListener}s are {@code @EventListener}s. With one process there is
 * no network hop to lose a message on, so the guarantee is actually stronger than
 * the at-least-once the broker gave — and the outbox in front of this still means
 * the write is durable before anything is dispatched.
 *
 * Failures are swallowed deliberately. A projection that cannot keep up must never
 * fail the user's write, which is exactly the behaviour a broker's asynchrony gave
 * for free; here it has to be spelled out. Each handler runs in its own transaction
 * (see the {@code REQUIRES_NEW} on the listeners), so a handler that rolls back
 * rolls back only itself.
 */
@Component
public class InProcessEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InProcessEventPublisher.class);

    private final ApplicationEventPublisher publisher;

    public InProcessEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(String topic, DomainEvent event) {
        try {
            publisher.publishEvent(new TopicEvent(topic, event));
            log.debug("Dispatched {} ({}) on {}", event.eventType(), event.eventId(), topic);
        } catch (RuntimeException ex) {
            log.error("Handler failed for {} ({}) on {}", event.eventType(), event.eventId(), topic, ex);
        }
    }
}
