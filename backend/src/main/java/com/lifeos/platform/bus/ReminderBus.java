package com.lifeos.platform.bus;

import com.lifeos.common.event.ReminderMessage;
import com.lifeos.common.event.SummaryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * What replaced RabbitMQ.
 *
 * Two message flows travelled the queue and both survive unchanged in shape:
 * schedulers produce {@link ReminderMessage}s that the notification package turns
 * into notifications, and the notification package produces {@link SummaryRequest}s
 * that the planning package answers. Neither side knows about the other's tables,
 * which was the point of the queue; the difference is only that the hop is a method
 * call now.
 *
 * The queue's dead-letter machinery is gone with it, and it is not missed: a
 * reminder that cannot be built once will not build on a retry either, and the
 * dedupe key on the consuming side — not the broker — was always what made
 * re-delivery harmless.
 */
@Component
public class ReminderBus {

    private static final Logger log = LoggerFactory.getLogger(ReminderBus.class);

    private final ApplicationEventPublisher publisher;

    public ReminderBus(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void sendReminder(ReminderMessage message) {
        try {
            publisher.publishEvent(message);
        } catch (RuntimeException ex) {
            log.error("Could not deliver reminder {}: {}", message.dedupeKey(), ex.getMessage(), ex);
        }
    }

    public void requestSummary(SummaryRequest request) {
        try {
            publisher.publishEvent(request);
        } catch (RuntimeException ex) {
            log.error("Could not request the summary for {}: {}", request.userId(), ex.getMessage(), ex);
        }
    }
}
