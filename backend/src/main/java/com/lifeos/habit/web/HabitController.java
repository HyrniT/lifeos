package com.lifeos.habit.web;

import com.lifeos.common.security.UserPrincipal;
import com.lifeos.habit.dto.HabitDtos.*;
import com.lifeos.habit.service.HabitCommandService;
import com.lifeos.habit.service.HabitQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/habits")
@Tag(name = "Habits")
public class HabitController {

    private final HabitCommandService commands;
    private final HabitQueryService queries;

    public HabitController(HabitCommandService commands, HabitQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    // ------------------------------------------------------------- queries
    @GetMapping
    @Operation(summary = "Every habit for the signed-in user")
    public List<HabitResponse> list(@AuthenticationPrincipal UserPrincipal me,
                                    @RequestParam(defaultValue = "false") boolean includeArchived) {
        return queries.list(me.id(), includeArchived);
    }

    @GetMapping("/today")
    @Operation(summary = "What is due today, with progress and RPG stats")
    public TodaySummary today(@AuthenticationPrincipal UserPrincipal me) {
        return queries.today(me.id());
    }

    @GetMapping("/heatmap")
    @Operation(summary = "Contribution heatmap across all habits")
    public List<HeatmapCell> heatmap(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(364) : from;
        return queries.heatmap(me.id(), start, end);
    }

    @GetMapping("/{id}")
    public HabitResponse get(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id) {
        return queries.get(me.id(), id);
    }

    @GetMapping("/{id}/logs")
    @Operation(summary = "Check-in history for one habit")
    public List<LogEntry> logs(
            @AuthenticationPrincipal UserPrincipal me,
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(89) : from;
        return queries.logsFor(me.id(), id, start, end);
    }

    @GetMapping("/{id}/insights")
    @Operation(summary = "Completion rates, weekday profile, mood average and trend")
    public HabitInsights insights(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id) {
        return queries.insights(me.id(), id);
    }

    // ------------------------------------------------------------ commands
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HabitResponse create(@AuthenticationPrincipal UserPrincipal me,
                                @Valid @RequestBody CreateHabitRequest req) {
        return commands.create(me.id(), req);
    }

    @PatchMapping("/{id}")
    public HabitResponse update(@AuthenticationPrincipal UserPrincipal me,
                                @PathVariable UUID id,
                                @Valid @RequestBody UpdateHabitRequest req) {
        return commands.update(me.id(), id, req);
    }

    @PostMapping("/{id}/archive")
    public HabitResponse archive(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id) {
        return commands.setArchived(me.id(), id, true);
    }

    @PostMapping("/{id}/unarchive")
    public HabitResponse unarchive(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id) {
        return commands.setArchived(me.id(), id, false);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id) {
        commands.delete(me.id(), id);
    }

    @PostMapping("/reorder")
    @Operation(summary = "Persist a drag-and-drop reordering")
    public List<HabitResponse> reorder(@AuthenticationPrincipal UserPrincipal me,
                                       @RequestBody ReorderRequest req) {
        commands.reorder(me.id(), req.orderedIds());
        return queries.list(me.id(), false);
    }

    @PostMapping("/{id}/check-in")
    @Operation(summary = "Complete a habit for a date (defaults to today)")
    public CheckInResponse checkIn(@AuthenticationPrincipal UserPrincipal me,
                                   @PathVariable UUID id,
                                   @Valid @RequestBody(required = false) CheckInRequest req) {
        return commands.checkIn(me.id(), id, req == null
                ? new CheckInRequest(null, null, null, null) : req);
    }

    @DeleteMapping("/{id}/check-in")
    @Operation(summary = "Undo a check-in")
    public HabitResponse undoCheckIn(
            @AuthenticationPrincipal UserPrincipal me,
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return commands.undoCheckIn(me.id(), id, date);
    }

    @PostMapping("/projections/rebuild")
    @Operation(summary = "Rebuild this user's read model from the event store")
    public Map<String, Object> rebuild(@AuthenticationPrincipal UserPrincipal me) {
        int replayed = commands.rebuildProjections(me.id());
        return Map.of("replayedEvents", replayed, "status", "rebuilt");
    }
}
