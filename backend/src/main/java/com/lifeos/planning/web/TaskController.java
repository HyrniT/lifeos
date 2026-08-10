package com.lifeos.planning.web;

import com.lifeos.common.security.UserPrincipal;
import com.lifeos.planning.domain.PlanningEnums.TaskStatus;
import com.lifeos.planning.dto.PlanningDtos.*;
import com.lifeos.planning.service.PlanningService;
import com.lifeos.planning.service.PlanningStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Tasks")
public class TaskController {

    private final PlanningService planning;
    private final PlanningStatsService stats;

    public TaskController(PlanningService planning, PlanningStatsService stats) {
        this.planning = planning;
        this.stats = stats;
    }

    @GetMapping
    public List<TaskResponse> list(@AuthenticationPrincipal UserPrincipal me,
                                   @RequestParam(required = false) TaskStatus status,
                                   @RequestParam(required = false) UUID projectId,
                                   @RequestParam(required = false) UUID goalId,
                                   @RequestParam(required = false) String search) {
        return planning.listTasks(me.id(), status, projectId, goalId, search);
    }

    @GetMapping("/agenda")
    @Operation(summary = "Overdue, due-today and scheduled work for one day")
    public AgendaResponse agenda(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return planning.agenda(me.id(), date);
    }

    @GetMapping("/statistics")
    @Operation(summary = "Productivity analytics: throughput, focus, quadrants, goals")
    public PlanningStatistics statistics(@AuthenticationPrincipal UserPrincipal me,
                                         @RequestParam(defaultValue = "30") int days) {
        return stats.compute(me.id(), Math.min(365, Math.max(7, days)));
    }

    @GetMapping("/{id}")
    public TaskResponse get(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id) {
        return planning.getTask(me.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@AuthenticationPrincipal UserPrincipal me,
                               @Valid @RequestBody TaskRequest req) {
        return planning.createTask(me.id(), req);
    }

    @PutMapping("/{id}")
    public TaskResponse update(@AuthenticationPrincipal UserPrincipal me,
                               @PathVariable UUID id, @Valid @RequestBody TaskRequest req) {
        return planning.updateTask(me.id(), id, req);
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "Move a task between TODO / IN_PROGRESS / DONE / CANCELLED")
    public TaskResponse setStatus(@AuthenticationPrincipal UserPrincipal me,
                                  @PathVariable UUID id,
                                  @RequestParam TaskStatus status) {
        return planning.setStatus(me.id(), id, status);
    }

    @PostMapping("/reorder")
    public List<TaskResponse> reorder(@AuthenticationPrincipal UserPrincipal me,
                                      @RequestBody ReorderRequest req) {
        planning.reorderTasks(me.id(), req.orderedIds());
        return planning.listTasks(me.id(), null, null, null, null);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id) {
        planning.deleteTask(me.id(), id);
    }
}
