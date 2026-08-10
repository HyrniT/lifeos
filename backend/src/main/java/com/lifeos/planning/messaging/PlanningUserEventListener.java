package com.lifeos.planning.messaging;

import com.lifeos.common.event.DomainEvent;
import com.lifeos.common.event.Topics;
import com.lifeos.planning.domain.UserSettings;
import com.lifeos.planning.repo.PlanningUserSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.lifeos.platform.bus.TopicEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

/** Keeps the local timezone projection current so reminders fire in local time. */
@Component
public class PlanningUserEventListener {

    private static final Logger log = LoggerFactory.getLogger(PlanningUserEventListener.class);

    private final PlanningUserSettingsRepository userSettings;

    public PlanningUserEventListener(PlanningUserSettingsRepository userSettings) {
        this.userSettings = userSettings;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserEvent(TopicEvent envelope) {
        if (!Topics.USER_EVENTS.equals(envelope.topic())) {
            return;
        }
        DomainEvent event = envelope.event();
        if (event.userId() == null) {
            return;
        }
        Map<String, Object> payload = event.payload() == null ? Map.of() : event.payload();
        Object zone = payload.get("timezone");
        if (zone == null) {
            return;
        }

        String timezone = String.valueOf(zone);
        try {
            ZoneId.of(timezone);
        } catch (Exception ex) {
            // A bad zone would make every reminder for this user throw; keep UTC.
            log.warn("Ignoring unknown timezone '{}' for user {}", timezone, event.userId());
            return;
        }

        UUID userId = event.userId();
        UserSettings settings = userSettings.findById(userId)
                .orElseGet(() -> UserSettings.builder().userId(userId).build());
        settings.setTimezone(timezone);
        settings.setUpdatedAt(Instant.now());
        userSettings.save(settings);
    }
}
