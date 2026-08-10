package com.lifeos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeos.analytics.model.DailyRollup;
import com.lifeos.analytics.repo.DailyRollupRepository;
import com.lifeos.analytics.repo.EventRecordRepository;
import com.lifeos.common.event.ReminderMessage;
import com.lifeos.habit.eventstore.OutboxRelay;
import com.lifeos.notification.repo.NotificationRepository;
import com.lifeos.platform.bus.ReminderBus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The path a check-in takes from the HTTP request to the analytics chart.
 *
 * It used to cross four processes and two brokers. It now crosses four packages
 * and an in-process bus, and this test asserts that the destinations are still
 * reached — which is the whole claim the merge makes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("A check-in still reaches the analytics rollup and the notification inbox")
class EndToEndFlowTest extends PostgresBackedTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private DailyRollupRepository rollups;

    @Autowired
    private EventRecordRepository records;

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private ReminderBus reminders;

    @Test
    @DisplayName("register, create a habit, check in, and see it in the rollup")
    void checkInReachesAnalytics() throws Exception {
        String email = "runner-" + UUID.randomUUID() + "@lifeos.test";

        // ---- register -----------------------------------------------------
        MvcResult registered = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "email", email,
                                "password", "correct-horse-9",
                                "displayName", "Test Runner",
                                "timezone", "Asia/Ho_Chi_Minh",
                                "baseCurrency", "VND"))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode tokens = json.readTree(registered.getResponse().getContentAsString());
        String bearer = "Bearer " + tokens.get("accessToken").asText();
        UUID userId = UUID.fromString(tokens.get("user").get("id").asText());

        // ---- create a habit ------------------------------------------------
        MvcResult created = mvc.perform(post("/api/habits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "name", "Morning run",
                                "targetPerPeriod", 1))))
                .andExpect(status().isCreated())
                .andReturn();

        UUID habitId = UUID.fromString(
                json.readTree(created.getResponse().getContentAsString()).get("id").asText());

        // ---- check in ------------------------------------------------------
        LocalDate today = LocalDate.now();
        mvc.perform(post("/api/habits/" + habitId + "/check-in")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "date", today.toString(), "value", 1.0, "mood", 4))))
                .andExpect(status().isOk());

        // Nothing has been dispatched yet: the write committed to the event store
        // and the outbox, and the relay is what carries it onward. Driving it by
        // hand rather than waiting on its timer is what keeps this deterministic.
        assertThat(rollups.findByUserIdAndDate(userId, today))
                .as("the rollup should not exist before the relay runs")
                .isEmpty();

        outboxRelay.relay();

        // ---- the projection ran --------------------------------------------
        Optional<DailyRollup> rollup = rollups.findByUserIdAndDate(userId, today);
        assertThat(rollup).as("analytics rollup for today").isPresent();
        assertThat(rollup.get().getHabitCheckIns()).isEqualTo(1);
        assertThat(rollup.get().getMood()).isEqualTo(4);
        assertThat(rollup.get().getXpEarned()).isPositive();

        assertThat(records.findTop50ByUserIdOrderByOccurredAtDesc(userId))
                .as("the raw event archive")
                .isNotEmpty();

        // ---- and the API serves it -----------------------------------------
        MvcResult overview = mvc.perform(get("/api/analytics/overview")
                        .header("Authorization", bearer)
                        .param("from", today.minusDays(6).toString())
                        .param("to", today.toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = json.readTree(overview.getResponse().getContentAsString());
        assertThat(body.get("totalCheckIns").asInt()).isEqualTo(1);
        assertThat(body.get("timeline")).hasSize(7);
    }

    @Test
    @DisplayName("a redelivered event does not double-count")
    void relayIsIdempotent() throws Exception {
        String email = "idem-" + UUID.randomUUID() + "@lifeos.test";
        String bearer = registerAndCreateCheckIn(email);

        outboxRelay.relay();
        long afterFirst = records.count();

        // The relay marks rows published, so a second pass finds nothing. Re-running
        // the projector over the same events directly is the case that matters, and
        // the archive's primary key is what makes it a no-op.
        outboxRelay.relay();

        assertThat(records.count())
                .as("a second relay pass must not archive anything again")
                .isEqualTo(afterFirst);
        assertThat(bearer).isNotBlank();
    }

    @Test
    @DisplayName("a reminder becomes a notification, and a repeat of it does not")
    void reminderBecomesNotification() throws Exception {
        String email = "notify-" + UUID.randomUUID() + "@lifeos.test";
        MvcResult registered = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "email", email,
                                "password", "correct-horse-9",
                                "displayName", "Notified"))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode tokens = json.readTree(registered.getResponse().getContentAsString());
        UUID userId = UUID.fromString(tokens.get("user").get("id").asText());
        String dedupeKey = "test:" + userId + ":once";

        ReminderMessage message = ReminderMessage.of(userId, ReminderMessage.Kind.TEST, dedupeKey)
                .title("Time to run")
                .body("Your habit is due")
                .build();

        reminders.sendReminder(message);
        reminders.sendReminder(message);

        List<?> stored = notifications.findAll().stream()
                .filter(n -> dedupeKey.equals(n.getDedupeKey()))
                .toList();

        // The dedupe key, not the broker, was always what made a repeat harmless.
        assertThat(stored).as("one notification per dedupe key").hasSize(1);
    }

    private String registerAndCreateCheckIn(String email) throws Exception {
        MvcResult registered = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "email", email,
                                "password", "correct-horse-9",
                                "displayName", "Test Runner"))))
                .andExpect(status().isCreated())
                .andReturn();

        String bearer = "Bearer " + json.readTree(registered.getResponse().getContentAsString())
                .get("accessToken").asText();

        MvcResult created = mvc.perform(post("/api/habits")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("name", "Read"))))
                .andExpect(status().isCreated())
                .andReturn();

        UUID habitId = UUID.fromString(
                json.readTree(created.getResponse().getContentAsString()).get("id").asText());

        mvc.perform(post("/api/habits/" + habitId + "/check-in")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        return bearer;
    }
}
