package com.lifeos.planning.web;

import com.lifeos.common.security.UserPrincipal;
import com.lifeos.planning.domain.PlanningEnums.GoalStatus;
import com.lifeos.planning.dto.PlanningDtos.*;
import com.lifeos.planning.service.PlanningService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Goals and projects share a controller because their CRUD surfaces are identical in shape. */
@RestController
@Tag(name = "Goals & projects")
public class GoalProjectController {

    private final PlanningService planning;

    public GoalProjectController(PlanningService planning) {
        this.planning = planning;
    }

    // ================================================================= goals
    @GetMapping("/api/goals")
    public List<GoalResponse> listGoals(@AuthenticationPrincipal UserPrincipal me,
                                        @RequestParam(required = false) GoalStatus status) {
        return planning.listGoals(me.id(), status);
    }

    @PostMapping("/api/goals")
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse createGoal(@AuthenticationPrincipal UserPrincipal me,
                                   @Valid @RequestBody GoalRequest req) {
        return planning.createGoal(me.id(), req);
    }

    @PutMapping("/api/goals/{id}")
    public GoalResponse updateGoal(@AuthenticationPrincipal UserPrincipal me,
                                   @PathVariable UUID id, @RequestBody GoalRequest req) {
        return planning.updateGoal(me.id(), id, req);
    }

    @DeleteMapping("/api/goals/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGoal(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id) {
        planning.deleteGoal(me.id(), id);
    }

    // ============================================================== projects
    @GetMapping("/api/projects")
    public List<ProjectResponse> listProjects(@AuthenticationPrincipal UserPrincipal me) {
        return planning.listProjects(me.id());
    }

    @PostMapping("/api/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse createProject(@AuthenticationPrincipal UserPrincipal me,
                                         @Valid @RequestBody ProjectRequest req) {
        return planning.createProject(me.id(), req);
    }

    @PutMapping("/api/projects/{id}")
    public ProjectResponse updateProject(@AuthenticationPrincipal UserPrincipal me,
                                         @PathVariable UUID id, @RequestBody ProjectRequest req) {
        return planning.updateProject(me.id(), id, req);
    }

    @DeleteMapping("/api/projects/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProject(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id) {
        planning.deleteProject(me.id(), id);
    }
}
