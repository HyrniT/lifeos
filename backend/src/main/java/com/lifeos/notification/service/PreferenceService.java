package com.lifeos.notification.service;

import com.lifeos.notification.domain.NotificationPreference;
import com.lifeos.notification.domain.UserSettings;
import com.lifeos.notification.repo.PreferenceRepository;
import com.lifeos.notification.repo.NotificationUserSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class PreferenceService {

    private final PreferenceRepository preferences;
    private final NotificationUserSettingsRepository userSettings;

    public PreferenceService(PreferenceRepository preferences, NotificationUserSettingsRepository userSettings) {
        this.preferences = preferences;
        this.userSettings = userSettings;
    }

    /**
     * Preferences are created lazily on first use, seeded with the user's timezone
     * from the profile projection. Creating them at registration would mean a user
     * who registered before this feature existed silently gets no reminders.
     */
    @Transactional
    public NotificationPreference forUser(UUID userId) {
        return preferences.findById(userId).orElseGet(() -> {
            String zone = userSettings.findById(userId)
                    .map(UserSettings::getTimezone)
                    .orElse("UTC");
            return preferences.save(NotificationPreference.defaultsFor(userId, zone));
        });
    }

    @Transactional
    public NotificationPreference update(UUID userId, PreferenceUpdate update) {
        NotificationPreference preference = forUser(userId);

        if (update.inAppEnabled() != null) {
            preference.setInAppEnabled(update.inAppEnabled());
        }
        if (update.pushEnabled() != null) {
            preference.setPushEnabled(update.pushEnabled());
        }
        if (update.emailEnabled() != null) {
            preference.setEmailEnabled(update.emailEnabled());
        }
        if (update.mutedKinds() != null) {
            preference.setMutedKinds(new LinkedHashSet<>(update.mutedKinds()));
        }
        if (update.leadTimeMinutes() != null) {
            // Sorted descending so the earliest warning is listed first in the UI,
            // and de-duplicated because a repeated lead time would double-notify.
            Set<Integer> cleaned = new java.util.TreeSet<>(java.util.Comparator.reverseOrder());
            update.leadTimeMinutes().stream()
                    .filter(m -> m != null && m > 0 && m <= 60 * 24 * 14)
                    .forEach(cleaned::add);
            preference.setLeadTimeMinutes(new LinkedHashSet<>(cleaned));
        }
        if (update.remindAtDeadline() != null) {
            preference.setRemindAtDeadline(update.remindAtDeadline());
        }
        if (update.remindWhenOverdue() != null) {
            preference.setRemindWhenOverdue(update.remindWhenOverdue());
        }
        if (update.dailySummaryEnabled() != null) {
            preference.setDailySummaryEnabled(update.dailySummaryEnabled());
        }
        if (update.dailySummaryTime() != null) {
            preference.setDailySummaryTime(update.dailySummaryTime());
        }
        if (update.quietHoursEnabled() != null) {
            preference.setQuietHoursEnabled(update.quietHoursEnabled());
        }
        if (update.quietFrom() != null) {
            preference.setQuietFrom(update.quietFrom());
        }
        if (update.quietTo() != null) {
            preference.setQuietTo(update.quietTo());
        }
        if (update.timezone() != null && !update.timezone().isBlank()) {
            preference.setTimezone(update.timezone());
        }

        preference.setUpdatedAt(Instant.now());
        return preferences.save(preference);
    }

    /** Applied when the user profile changes, so quiet hours follow them across zones. */
    @Transactional
    public void syncTimezone(UUID userId, String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return;
        }
        preferences.findById(userId).ifPresent(preference -> {
            if (!timezone.equals(preference.getTimezone())) {
                preference.setTimezone(timezone);
                preference.setUpdatedAt(Instant.now());
                preferences.save(preference);
            }
        });
    }

    public record PreferenceUpdate(
            Boolean inAppEnabled,
            Boolean pushEnabled,
            Boolean emailEnabled,
            Set<String> mutedKinds,
            Set<Integer> leadTimeMinutes,
            Boolean remindAtDeadline,
            Boolean remindWhenOverdue,
            Boolean dailySummaryEnabled,
            LocalTime dailySummaryTime,
            Boolean quietHoursEnabled,
            LocalTime quietFrom,
            LocalTime quietTo,
            String timezone
    ) {
    }
}
