package com.lifeos.notification.web;

import com.lifeos.common.event.ReminderMessage;
import com.lifeos.common.security.UserPrincipal;
import com.lifeos.notification.domain.NotificationPreference;
import com.lifeos.notification.domain.PushSubscription;
import com.lifeos.notification.push.VapidKeys;
import com.lifeos.notification.push.WebPushSender;
import com.lifeos.notification.repo.PushSubscriptionRepository;
import com.lifeos.notification.service.NotificationService;
import com.lifeos.notification.service.PreferenceService;
import com.lifeos.notification.web.dto.NotificationView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService notifications;
    private final PreferenceService preferences;
    private final PushSubscriptionRepository subscriptions;
    private final NotificationStreamController stream;
    private final VapidKeys vapid;
    private final WebPushSender push;

    public NotificationController(NotificationService notifications, PreferenceService preferences,
                                  PushSubscriptionRepository subscriptions,
                                  NotificationStreamController stream, VapidKeys vapid,
                                  @Autowired(required = false) WebPushSender push) {
        this.notifications = notifications;
        this.preferences = preferences;
        this.subscriptions = subscriptions;
        this.stream = stream;
        this.vapid = vapid;
        this.push = push;
    }

    // ================================================================= inbox
    @GetMapping
    public List<NotificationView> list(@AuthenticationPrincipal UserPrincipal me,
                                       @RequestParam(defaultValue = "false") boolean unreadOnly,
                                       @RequestParam(defaultValue = "60") int limit) {
        return notifications.inbox(me.id(), unreadOnly, limit).stream()
                .map(NotificationView::from).toList();
    }

    @GetMapping("/unread-count")
    public Map<String, Object> unreadCount(@AuthenticationPrincipal UserPrincipal me) {
        return Map.of("unread", notifications.unreadCount(me.id()));
    }

    @PostMapping("/{id}/read")
    public Map<String, Object> markRead(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id) {
        return Map.of("updated", notifications.markRead(me.id(), id) ? 1 : 0);
    }

    @PostMapping("/read-all")
    public Map<String, Object> markAllRead(@AuthenticationPrincipal UserPrincipal me) {
        return Map.of("updated", notifications.markAllRead(me.id()));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal UserPrincipal me) {
        notifications.clear(me.id());
    }

    // =========================================================== preferences
    @GetMapping("/preferences")
    @Operation(summary = "What this user wants to be told about, and when")
    public PreferenceView preferences(@AuthenticationPrincipal UserPrincipal me) {
        return PreferenceView.from(preferences.forUser(me.id()),
                push != null && push.isAvailable(),
                subscriptions.countByUserId(me.id()));
    }

    @PutMapping("/preferences")
    public PreferenceView updatePreferences(@AuthenticationPrincipal UserPrincipal me,
                                            @RequestBody PreferenceService.PreferenceUpdate update) {
        return PreferenceView.from(preferences.update(me.id(), update),
                push != null && push.isAvailable(),
                subscriptions.countByUserId(me.id()));
    }

    @GetMapping("/kinds")
    @Operation(summary = "Every notification kind the user can mute, for the settings UI")
    public List<Map<String, String>> kinds() {
        return List.of(
                kind(ReminderMessage.Kind.TASK_DUE_SOON, "Task due soon", "Ahead of a task's deadline"),
                kind(ReminderMessage.Kind.TASK_DUE, "Task due now", "At the deadline itself"),
                kind(ReminderMessage.Kind.TASK_OVERDUE, "Task overdue", "The morning after a missed deadline"),
                kind(ReminderMessage.Kind.HABIT_DUE, "Habit reminder", "At the time you chose for each habit"),
                kind(ReminderMessage.Kind.HABIT_STREAK_AT_RISK, "Streak at risk", "Late in the day with a streak unfinished"),
                kind(ReminderMessage.Kind.GOAL_DEADLINE, "Goal deadline", "As a goal's target date approaches"),
                kind(ReminderMessage.Kind.DAILY_SUMMARY, "Daily summary", "One morning digest of what is due"),
                kind(ReminderMessage.Kind.BUDGET_WARNING, "Budget warning", "When a budget nears its limit"),
                kind(ReminderMessage.Kind.BUDGET_EXCEEDED, "Budget exceeded", "When a budget goes over"),
                kind(ReminderMessage.Kind.STREAK_MILESTONE, "Streak milestones", "7, 30, 100 days and beyond"),
                kind(ReminderMessage.Kind.ACHIEVEMENT, "Achievements", "When you unlock one"),
                kind(ReminderMessage.Kind.LEVEL_UP, "Level ups", "When you gain a level"));
    }

    private static Map<String, String> kind(String code, String label, String description) {
        return Map.of("code", code, "label", label, "description", description);
    }

    // ================================================================== push
    @GetMapping("/push/key")
    @Operation(summary = "VAPID public key the browser needs to subscribe")
    public Map<String, Object> vapidKey() {
        return Map.of(
                "publicKey", vapid.publicKey(),
                "available", push != null && push.isAvailable(),
                // Surfaced so the UI can warn that subscriptions will not survive a
                // restart on a deployment that never pinned its keys.
                "persistent", vapid.isPersistent());
    }

    @PostMapping("/push/subscribe")
    @Transactional
    @Operation(summary = "Register this browser for Web Push")
    public Map<String, Object> subscribe(@AuthenticationPrincipal UserPrincipal me,
                                         @RequestBody SubscribeRequest request,
                                         HttpServletRequest http) {
        // Endpoints are unique per browser: re-subscribing after a permission reset
        // must update the existing row, not accumulate duplicates.
        PushSubscription subscription = subscriptions.findByEndpoint(request.endpoint())
                .orElseGet(() -> PushSubscription.builder().endpoint(request.endpoint()).build());

        subscription.setUserId(me.id());
        subscription.setP256dh(request.p256dh());
        subscription.setAuth(request.auth());
        subscription.setFailureCount(0);
        String agent = http.getHeader("User-Agent");
        subscription.setUserAgent(agent == null ? null : agent.substring(0, Math.min(agent.length(), 256)));
        if (subscription.getCreatedAt() == null) {
            subscription.setCreatedAt(Instant.now());
        }
        subscriptions.save(subscription);

        return Map.of("subscribed", true, "devices", subscriptions.countByUserId(me.id()));
    }

    @PostMapping("/push/unsubscribe")
    @Transactional
    public Map<String, Object> unsubscribe(@AuthenticationPrincipal UserPrincipal me,
                                           @RequestBody SubscribeRequest request) {
        subscriptions.findByEndpoint(request.endpoint())
                .filter(s -> s.getUserId().equals(me.id()))
                .ifPresent(subscriptions::delete);
        return Map.of("subscribed", false, "devices", subscriptions.countByUserId(me.id()));
    }

    @PostMapping("/test")
    @Operation(summary = "Send yourself a notification to prove the whole chain works")
    public Map<String, Object> test(@AuthenticationPrincipal UserPrincipal me) {
        // A unique key each time, and marked urgent so quiet hours do not swallow
        // the one notification the user is actively waiting for.
        String key = "test:" + me.id() + ":" + Instant.now().toEpochMilli();
        var sent = notifications.accept(ReminderMessage.of(me.id(), ReminderMessage.Kind.TEST, key)
                .title("LifeOS notifications are working")
                .body("If this arrived on your device with the tab closed, Web Push is set up correctly.")
                .icon("bell-ring")
                .severity(ReminderMessage.Severity.INFO)
                .deepLink("/settings")
                .urgent(true)
                .build());

        return Map.of(
                "sent", sent.isPresent(),
                "pushDevices", subscriptions.countByUserId(me.id()),
                "openTabs", stream.connectedClients());
    }

    @GetMapping("/diagnostics")
    public Map<String, Object> diagnostics(@AuthenticationPrincipal UserPrincipal me) {
        return Map.of(
                "connectedClients", stream.connectedClients(),
                "pushAvailable", push != null && push.isAvailable(),
                "pushDevices", subscriptions.countByUserId(me.id()),
                "vapidPersistent", vapid.isPersistent());
    }

    // ================================================================= DTOs
    public record SubscribeRequest(
            @NotBlank String endpoint,
            @NotBlank String p256dh,
            @NotBlank String auth
    ) {
    }

    public record PreferenceView(
            boolean inAppEnabled,
            boolean pushEnabled,
            boolean emailEnabled,
            Set<String> mutedKinds,
            Set<Integer> leadTimeMinutes,
            boolean remindAtDeadline,
            boolean remindWhenOverdue,
            boolean dailySummaryEnabled,
            LocalTime dailySummaryTime,
            boolean quietHoursEnabled,
            LocalTime quietFrom,
            LocalTime quietTo,
            String timezone,
            boolean pushAvailable,
            long pushDevices
    ) {
        static PreferenceView from(NotificationPreference p, boolean pushAvailable, long devices) {
            return new PreferenceView(p.isInAppEnabled(), p.isPushEnabled(), p.isEmailEnabled(),
                    p.getMutedKinds(), p.getLeadTimeMinutes(), p.isRemindAtDeadline(),
                    p.isRemindWhenOverdue(), p.isDailySummaryEnabled(), p.getDailySummaryTime(),
                    p.isQuietHoursEnabled(), p.getQuietFrom(), p.getQuietTo(), p.getTimezone(),
                    pushAvailable, devices);
        }
    }
}
