package com.lifeos.analytics.web;

import com.lifeos.analytics.model.EventRecord;
import com.lifeos.analytics.repo.EventRecordRepository;
import com.lifeos.analytics.service.InsightService;
import com.lifeos.common.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Cross-domain analytics")
public class AnalyticsController {

    private final InsightService insights;
    private final EventRecordRepository events;

    public AnalyticsController(InsightService insights, EventRecordRepository events) {
        this.insights = insights;
        this.events = events;
    }

    @GetMapping("/analytics/overview")
    @Operation(summary = "Habits, money, productivity and wellbeing on one timeline")
    public InsightService.LifeOverview overview(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(89) : from;
        return insights.overview(me.id(), start, end);
    }

    @GetMapping("/insights")
    @Operation(summary = "Correlations found across the user's own data")
    public List<InsightService.Correlation> correlations(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam(defaultValue = "90") int days) {
        LocalDate end = LocalDate.now();
        return insights.overview(me.id(), end.minusDays(Math.max(14, days) - 1L), end).correlations();
    }

    @GetMapping("/analytics/activity")
    @Operation(summary = "The most recent domain events for this user")
    public List<EventRecord> activity(@AuthenticationPrincipal UserPrincipal me) {
        return events.findTop50ByUserIdOrderByOccurredAtDesc(me.id());
    }
}
