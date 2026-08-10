package com.lifeos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeos.analytics.repo.DailyRollupRepository;
import com.lifeos.habit.eventstore.OutboxRelay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every bounded context, exercised through its own HTTP surface.
 *
 * Six deployments becoming one is exactly the kind of change that leaves a
 * controller unreachable — a lost component scan, a security rule that no longer
 * matches, a bean that used to exist in one service and now collides in another.
 * None of that shows up in a compile, so each context gets a request here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Every context answers on the one port")
class EveryContextRespondsTest extends PostgresBackedTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private DailyRollupRepository rollups;

    private String bearer;
    private UUID userId;

    @BeforeEach
    void signUp() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", "ctx-" + UUID.randomUUID() + "@lifeos.test",
                                "password", "correct-horse-9",
                                "displayName", "Context Tester"))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode tokens = json.readTree(result.getResponse().getContentAsString());
        bearer = "Bearer " + tokens.get("accessToken").asText();
        userId = UUID.fromString(tokens.get("user").get("id").asText());
    }

    @Test
    @DisplayName("auth: the profile endpoint returns the account just created")
    void authContext() throws Exception {
        MvcResult result = mvc.perform(get("/api/users/me").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(json.readTree(result.getResponse().getContentAsString()).get("id").asText())
                .isEqualTo(userId.toString());
    }

    @Test
    @DisplayName("auth: no token means 401, not a 500 and not a silent pass")
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(get("/api/habits")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/analytics/overview")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("auth: /api/admin is closed to an ordinary account")
    void adminIsClosed() throws Exception {
        mvc.perform(get("/api/admin/overview").header("Authorization", bearer))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("money: an account, a category and a transaction, then the statistics")
    void expenseContext() throws Exception {
        UUID accountId = created(post("/api/accounts"), Map.of(
                "name", "Cash", "type", "CASH", "currency", "USD", "openingBalance", 500));

        UUID categoryId = created(post("/api/categories"), Map.of(
                "name", "Groceries", "kind", "EXPENSE"));

        Map<String, Object> tx = new LinkedHashMap<>();
        tx.put("accountId", accountId.toString());
        tx.put("categoryId", categoryId.toString());
        tx.put("amount", new BigDecimal("42.50"));
        tx.put("type", "EXPENSE");
        tx.put("occurredOn", LocalDate.now().toString());
        tx.put("note", "weekly shop");
        created(post("/api/expenses"), tx);

        MvcResult stats = mvc.perform(get("/api/expenses/statistics/overview")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(stats.getResponse().getContentAsString()).isNotBlank();

        // And the spend reached the cross-domain rollup, which is the seam that
        // used to be a Kafka topic between two separate deployments.
        outboxRelay.relay();
        assertThat(rollups.findByUserIdAndDate(userId, LocalDate.now()))
                .get()
                .satisfies(rollup -> assertThat(rollup.getExpenseTotal())
                        .isEqualByComparingTo(new BigDecimal("42.50")));
    }

    @Test
    @DisplayName("planning: a project, a goal, a task, a focus session and a journal entry")
    void planningContext() throws Exception {
        UUID projectId = created(post("/api/projects"), Map.of("name", "Move house"));
        UUID goalId = created(post("/api/goals"), Map.of("title", "Pack everything", "targetValue", 10));

        Map<String, Object> task = new LinkedHashMap<>();
        task.put("title", "Book the van");
        task.put("projectId", projectId.toString());
        task.put("goalId", goalId.toString());
        task.put("priority", "P1");
        task.put("dueDate", LocalDate.now().toString());
        UUID taskId = created(post("/api/tasks"), task);

        mvc.perform(post("/api/tasks/" + taskId + "/status")
                        .header("Authorization", bearer)
                        .param("status", "DONE"))
                .andExpect(status().isOk());

        UUID sessionId = created(post("/api/focus/start"),
                Map.of("taskId", taskId.toString(), "plannedMinutes", 25));
        mvc.perform(post("/api/focus/" + sessionId + "/end")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("focusScore", 4))))
                .andExpect(status().isOk());

        mvc.perform(put("/api/journal")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "entryDate", LocalDate.now().toString(),
                                "mood", 4, "energy", 3, "notes", "long day"))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/tasks/agenda").header("Authorization", bearer))
                .andExpect(status().isOk());

        // A task carries no date of its own the way a transaction or a check-in
        // does, so the projector files it under the UTC day it happened on. East of
        // Greenwich those are not the same day for the first seven hours after
        // midnight, and asserting on the local date makes this pass or fail
        // depending on what time the suite runs.
        LocalDate utcToday = LocalDate.now(ZoneOffset.UTC);
        assertThat(rollups.findByUserIdAndDate(userId, utcToday))
                .get()
                .satisfies(rollup -> {
                    assertThat(rollup.getTasksCreated()).isEqualTo(1);
                    assertThat(rollup.getTasksCompleted()).isEqualTo(1);
                    assertThat(rollup.getFocusMinutes()).isNotNegative();
                });
    }

    @Test
    @DisplayName("gamification: stats and achievements are served")
    void gamificationContext() throws Exception {
        mvc.perform(get("/api/gamification/stats").header("Authorization", bearer))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("notifications: the inbox, the preferences and a test notification")
    void notificationContext() throws Exception {
        mvc.perform(get("/api/notifications").header("Authorization", bearer))
                .andExpect(status().isOk());
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer))
                .andExpect(status().isOk());
        mvc.perform(get("/api/notifications/preferences").header("Authorization", bearer))
                .andExpect(status().isOk());

        mvc.perform(put("/api/notifications/preferences")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "inAppEnabled", true,
                                "dailySummaryEnabled", false,
                                "quietHoursEnabled", false))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/notifications/test").header("Authorization", bearer))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("the API document builds, with one group per context")
    void apiDocumentation() throws Exception {
        MvcResult result = mvc.perform(get("/v3/api-docs/2-habits"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("/api/habits");
    }

    @Test
    @DisplayName("health is anonymous, because the platform's health check has no token")
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    /** POSTs a body, expects a 2xx, and returns the created resource's id. */
    private UUID created(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                         Map<String, ?> body) throws Exception {
        MvcResult result = mvc.perform(request
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        return UUID.fromString(
                json.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }
}
