package com.lifeos.notification.messaging;

import com.lifeos.common.event.DomainEvent;
import com.lifeos.common.event.ReminderMessage;
import com.lifeos.common.event.Topics;
import com.lifeos.notification.domain.UserSettings;
import com.lifeos.notification.repo.NotificationUserSettingsRepository;
import com.lifeos.notification.service.NotificationService;
import com.lifeos.notification.service.PreferenceService;
import com.lifeos.platform.bus.TopicEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Two inputs, one funnel.
 *
 * One is scheduled work: reminders a scheduler decided to send. The other is
 * things that already happened — a streak milestone, an achievement, a budget
 * breach. Both become the same notification.
 *
 * Every handler runs in its own transaction so a notification that cannot be
 * written never rolls back the domain write that triggered it.
 */
@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationService notifications;
    private final PreferenceService preferences;
    private final NotificationUserSettingsRepository userSettings;

    public NotificationConsumer(NotificationService notifications, PreferenceService preferences,
                                NotificationUserSettingsRepository userSettings) {
        this.notifications = notifications;
        this.preferences = preferences;
        this.userSettings = userSettings;
    }

    // --------------------------------------------------------- scheduled work
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReminder(ReminderMessage message) {
        try {
            notifications.accept(message);
        } catch (Exception ex) {
            // Throwing would requeue and eventually dead-letter. A message that
            // cannot be processed once will not process on retry either, so log it
            // and acknowledge rather than looping.
            log.error("Discarding unprocessable reminder {}: {}", message.dedupeKey(), ex.getMessage(), ex);
        }
    }

    // ------------------------------------------------------------ domain events
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onGamification(TopicEvent envelope) {
        if (!Topics.GAMIFICATION_EVENTS.equals(envelope.topic())) {
            return;
        }
        DomainEvent event = envelope.event();
        if (event.userId() == null) {
            return;
        }
        switch (event.eventType()) {
            case Topics.Gamification.ACHIEVEMENT_UNLOCKED -> notifications.accept(
                    ReminderMessage.of(event.userId(), ReminderMessage.Kind.ACHIEVEMENT,
                                    "achievement:" + event.userId() + ":" + event.payload().get("code"))
                            .title("Achievement unlocked")
                            .body(String.valueOf(event.payload().getOrDefault("title", "New achievement")))
                            .icon("trophy")
                            .severity(ReminderMessage.Severity.SUCCESS)
                            .deepLink("/achievements")
                            .data(new HashMap<>(event.payload()))
                            .build());

            case Topics.Gamification.LEVEL_UP -> notifications.accept(
                    ReminderMessage.of(event.userId(), ReminderMessage.Kind.LEVEL_UP,
                                    "level:" + event.userId() + ":" + event.payload().get("level"))
                            .title("Level up")
                            .body("You reached level " + event.payload().getOrDefault("level", "?"))
                            .icon("sparkles")
                            .severity(ReminderMessage.Severity.SUCCESS)
                            .deepLink("/achievements")
                            .data(new HashMap<>(event.payload()))
                            .build());

            default -> log.trace("Ignoring gamification event {}", event.eventType());
        }
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onHabit(TopicEvent envelope) {
        if (!Topics.HABIT_EVENTS.equals(envelope.topic())) {
            return;
        }
        DomainEvent event = envelope.event();
        if (event.userId() == null || !Topics.Habit.STREAK_MILESTONE.equals(event.eventType())) {
            return;
        }
        Object streak = event.payload().get("streak");
        notifications.accept(
                ReminderMessage.of(event.userId(), ReminderMessage.Kind.STREAK_MILESTONE,
                                "streak:" + event.aggregateId() + ":" + streak)
                        .title(streak + "-day streak")
                        .body("\"%s\" has been going for %s days straight."
                                .formatted(event.payload().getOrDefault("habitName", "Your habit"), streak))
                        .icon("flame")
                        .severity(ReminderMessage.Severity.SUCCESS)
                        .deepLink("/habits")
                        .data(new HashMap<>(event.payload()))
                        .build());
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onExpense(TopicEvent envelope) {
        if (!Topics.EXPENSE_EVENTS.equals(envelope.topic())) {
            return;
        }
        DomainEvent event = envelope.event();
        if (event.userId() == null || !Topics.Expense.BUDGET_EXCEEDED.equals(event.eventType())) {
            return;
        }
        // Budget state is recomputed on every statistics read, so this event repeats
        // constantly. Keying the dedupe on the day makes it at most one alert a day.
        String today = java.time.LocalDate.now().toString();
        notifications.accept(
                ReminderMessage.of(event.userId(), ReminderMessage.Kind.BUDGET_EXCEEDED,
                                "budget:" + event.aggregateId() + ":" + today)
                        .title("Budget exceeded")
                        .body("\"%s\" is over its limit."
                                .formatted(event.payload().getOrDefault("name", "A budget")))
                        .icon("alert-triangle")
                        .severity(ReminderMessage.Severity.WARNING)
                        .deepLink("/money")
                        .data(new HashMap<>(event.payload()))
                        .build());
    }

    /**
     * Keeps the local user projection current so quiet hours are evaluated in the
     * user's own timezone rather than the server's.
     */
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
        String timezone = payload.get("timezone") == null ? null : String.valueOf(payload.get("timezone"));

        UserSettings settings = userSettings.findById(event.userId())
                .orElseGet(() -> UserSettings.builder().userId(event.userId()).build());
        if (payload.get("email") != null) {
            settings.setEmail(String.valueOf(payload.get("email")));
        }
        if (payload.get("displayName") != null) {
            settings.setDisplayName(String.valueOf(payload.get("displayName")));
        }
        if (timezone != null && !timezone.isBlank()) {
            settings.setTimezone(timezone);
        }
        settings.setUpdatedAt(Instant.now());
        userSettings.save(settings);

        preferences.syncTimezone(event.userId(), timezone);
    }
}
