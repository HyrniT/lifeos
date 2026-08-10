package com.lifeos.notification.service;

import com.lifeos.common.event.SummaryRequest;
import com.lifeos.notification.domain.NotificationPreference;
import com.lifeos.notification.repo.PreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.lifeos.platform.bus.ReminderBus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;

/**
 * Fires the "build my morning summary" request at each user's chosen local time.
 *
 * This package owns the *when* (the preferred time and the timezone live here);
 * the planning package assembles the content. Neither reads the other's tables.
 *
 * No bookkeeping of "already sent today" is needed: the resulting notification
 * carries a dedupe key of {@code summary:<user>:<date>}, so a repeated request
 * produces nothing.
 */
@Component
public class DailySummaryTrigger {

    private static final Logger log = LoggerFactory.getLogger(DailySummaryTrigger.class);

    /** Must be at least as long as the scan interval, or a summary time is skipped. */
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final PreferenceRepository preferences;
    private final ReminderBus reminders;

    public DailySummaryTrigger(PreferenceRepository preferences, ReminderBus reminders) {
        this.preferences = preferences;
        this.reminders = reminders;
    }

    @Scheduled(fixedDelayString = "${lifeos.notifications.summary-interval-ms:600000}")
    @Transactional(readOnly = true)
    public void requestSummaries() {
        List<NotificationPreference> all = preferences.findAll();
        Instant now = Instant.now();
        int requested = 0;

        for (NotificationPreference preference : all) {
            if (!preference.isDailySummaryEnabled()) {
                continue;
            }
            ZoneId zone = zoneOf(preference.getTimezone());
            ZonedDateTime local = now.atZone(zone);

            ZonedDateTime scheduled = local.with(preference.getDailySummaryTime())
                    .withSecond(0).withNano(0);

            // Fire once the moment has passed, but only just — a wider window would
            // send yesterday's summary to anyone whose service was down overnight.
            if (local.isBefore(scheduled) || local.isAfter(scheduled.plus(WINDOW))) {
                continue;
            }

            reminders.requestSummary(new SummaryRequest(preference.getUserId(),
                    local.toLocalDate(), preference.getTimezone()));
            requested++;
        }

        if (requested > 0) {
            log.info("Requested {} daily summar{}", requested, requested == 1 ? "y" : "ies");
        }
    }

    private static ZoneId zoneOf(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (Exception ex) {
            return ZoneOffset.UTC;
        }
    }
}
