package com.lifeos.planning.service;

import com.lifeos.common.event.DomainEvent;
import com.lifeos.common.event.EventPublisher;
import com.lifeos.common.event.Topics;
import com.lifeos.common.exception.ApiException;
import com.lifeos.planning.domain.*;
import com.lifeos.planning.domain.PlanningEnums.*;
import com.lifeos.planning.dto.PlanningDtos.*;
import com.lifeos.planning.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/** Tasks, projects and goals — the planning side of the app. */
@Service
public class PlanningService {

    private static final Logger log = LoggerFactory.getLogger(PlanningService.class);
    private static final Set<TaskStatus> CLOSED = Set.of(TaskStatus.DONE, TaskStatus.CANCELLED);

    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final GoalRepository goals;
    private final FocusSessionRepository focusSessions;
    private final JournalRepository journals;
    private final EventPublisher events;

    public PlanningService(TaskRepository tasks, ProjectRepository projects, GoalRepository goals,
                           FocusSessionRepository focusSessions, JournalRepository journals,
                           EventPublisher events) {
        this.tasks = tasks;
        this.projects = projects;
        this.goals = goals;
        this.focusSessions = focusSessions;
        this.journals = journals;
        this.events = events;
    }

    // ================================================================= tasks
    @Transactional(readOnly = true)
    public List<TaskResponse> listTasks(UUID userId, TaskStatus status, UUID projectId,
                                        UUID goalId, String search) {
        List<Task> roots = tasks.search(userId, status, projectId, goalId, true,
                blankToNull(search), TaskStatus.DONE);
        Map<UUID, String> projectNames = projectNames(userId);
        return roots.stream().map(t -> decorate(userId, t, projectNames)).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(UUID userId, UUID id) {
        Task task = requireTask(userId, id);
        return decorate(userId, task, projectNames(userId));
    }

    @Transactional
    public TaskResponse createTask(UUID userId, TaskRequest req) {
        Task task = tasks.save(Task.builder()
                .userId(userId)
                .title(req.title().trim())
                .notes(req.notes())
                .projectId(req.projectId())
                .goalId(req.goalId())
                .parentTaskId(req.parentTaskId())
                .priority(req.priority() == null ? Priority.P3 : req.priority())
                .status(req.status() == null ? TaskStatus.TODO : req.status())
                .dueDate(req.dueDate())
                .dueTime(req.dueTime())
                .scheduledFor(req.scheduledFor())
                .estimateMinutes(req.estimateMinutes())
                .recurrence(req.recurrence() == null ? Recurrence.NONE : req.recurrence())
                .tags(req.tags() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(req.tags()))
                .sortOrder(tasks.maxSortOrder(userId) + 1)
                .build());

        publishTask(Topics.Planning.TASK_CREATED, task);
        return decorate(userId, task, projectNames(userId));
    }

    @Transactional
    public TaskResponse updateTask(UUID userId, UUID id, TaskRequest req) {
        Task task = requireTask(userId, id);
        TaskStatus before = task.getStatus();

        if (req.title() != null && !req.title().isBlank()) {
            task.setTitle(req.title().trim());
        }
        task.setNotes(req.notes());
        task.setProjectId(req.projectId());
        task.setGoalId(req.goalId());
        if (req.priority() != null) {
            task.setPriority(req.priority());
        }
        if (req.status() != null) {
            task.setStatus(req.status());
        }
        task.setDueDate(req.dueDate());
        task.setDueTime(req.dueTime());
        task.setScheduledFor(req.scheduledFor());
        task.setEstimateMinutes(req.estimateMinutes());
        if (req.recurrence() != null) {
            task.setRecurrence(req.recurrence());
        }
        if (req.tags() != null) {
            task.setTags(new LinkedHashSet<>(req.tags()));
        }
        task.setUpdatedAt(Instant.now());

        applyStatusTransition(task, before, task.getStatus());
        tasks.save(task);

        publishTask(Topics.Planning.TASK_UPDATED, task);
        return decorate(userId, task, projectNames(userId));
    }

    @Transactional
    public TaskResponse setStatus(UUID userId, UUID id, TaskStatus status) {
        Task task = requireTask(userId, id);
        TaskStatus before = task.getStatus();
        task.setStatus(status);
        task.setUpdatedAt(Instant.now());
        applyStatusTransition(task, before, status);
        tasks.save(task);

        if (status == TaskStatus.DONE && before != TaskStatus.DONE) {
            publishTask(Topics.Planning.TASK_COMPLETED, task);
            spawnNextOccurrence(task);
        } else {
            publishTask(Topics.Planning.TASK_UPDATED, task);
        }
        return decorate(userId, task, projectNames(userId));
    }

    @Transactional
    public void deleteTask(UUID userId, UUID id) {
        Task task = requireTask(userId, id);
        tasks.deleteByParentTaskId(id);        // sub-tasks go with their parent
        tasks.delete(task);
        publishTask(Topics.Planning.TASK_DELETED, task);
    }

    @Transactional
    public void reorderTasks(UUID userId, List<UUID> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            Task task = requireTask(userId, orderedIds.get(i));
            task.setSortOrder(i);
            tasks.save(task);
        }
    }

    private void applyStatusTransition(Task task, TaskStatus before, TaskStatus after) {
        if (after == TaskStatus.DONE && before != TaskStatus.DONE) {
            task.setCompletedAt(Instant.now());
        } else if (after != TaskStatus.DONE) {
            task.setCompletedAt(null);
        }
    }

    /**
     * A completed recurring task creates its next instance rather than resetting
     * itself, so the history of what was done when stays intact.
     */
    private void spawnNextOccurrence(Task task) {
        if (task.getRecurrence() == Recurrence.NONE) {
            return;
        }
        LocalDate next = task.nextOccurrence();
        if (next == null) {
            return;
        }
        tasks.save(Task.builder()
                .userId(task.getUserId())
                .title(task.getTitle())
                .notes(task.getNotes())
                .projectId(task.getProjectId())
                .goalId(task.getGoalId())
                .priority(task.getPriority())
                .status(TaskStatus.TODO)
                .dueDate(next)
                .dueTime(task.getDueTime())
                .estimateMinutes(task.getEstimateMinutes())
                .recurrence(task.getRecurrence())
                .tags(new LinkedHashSet<>(task.getTags()))
                .sortOrder(task.getSortOrder())
                .build());
        log.debug("Spawned next occurrence of recurring task {} on {}", task.getId(), next);
    }

    // ============================================================== projects
    @Transactional(readOnly = true)
    public List<ProjectResponse> listProjects(UUID userId) {
        return projects.findByUserIdOrderBySortOrderAscNameAsc(userId).stream()
                .map(this::decorateProject).toList();
    }

    @Transactional
    public ProjectResponse createProject(UUID userId, ProjectRequest req) {
        Project project = projects.save(Project.builder()
                .userId(userId)
                .name(req.name().trim())
                .description(req.description())
                .icon(orDefault(req.icon(), "folder"))
                .color(orDefault(req.color(), "#111111"))
                .status(req.status() == null ? ProjectStatus.ACTIVE : req.status())
                .dueDate(req.dueDate())
                .sortOrder((int) projects.countByUserId(userId))
                .build());
        return decorateProject(project);
    }

    @Transactional
    public ProjectResponse updateProject(UUID userId, UUID id, ProjectRequest req) {
        Project project = projects.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Project", id));
        if (req.name() != null && !req.name().isBlank()) {
            project.setName(req.name().trim());
        }
        project.setDescription(req.description());
        if (req.icon() != null) {
            project.setIcon(req.icon());
        }
        if (req.color() != null) {
            project.setColor(req.color());
        }
        if (req.status() != null) {
            project.setStatus(req.status());
        }
        project.setDueDate(req.dueDate());
        return decorateProject(projects.save(project));
    }

    @Transactional
    public void deleteProject(UUID userId, UUID id) {
        Project project = projects.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Project", id));
        long open = tasks.countByProjectId(id) - tasks.countByProjectIdAndStatus(id, TaskStatus.DONE);
        if (open > 0) {
            throw ApiException.conflict("This project still has %d open task(s)".formatted(open));
        }
        projects.delete(project);
    }

    private ProjectResponse decorateProject(Project p) {
        long total = tasks.countByProjectId(p.getId());
        long done = tasks.countByProjectIdAndStatus(p.getId(), TaskStatus.DONE);
        double progress = total == 0 ? 0 : Math.round((double) done / total * 1000) / 1000.0;
        return new ProjectResponse(p.getId(), p.getName(), p.getDescription(), p.getIcon(), p.getColor(),
                p.getStatus(), p.getDueDate(), p.getSortOrder(), total, done, progress, p.getCreatedAt());
    }

    // ================================================================= goals
    @Transactional(readOnly = true)
    public List<GoalResponse> listGoals(UUID userId, GoalStatus status) {
        List<Goal> list = status == null
                ? goals.findByUserIdOrderByTargetDateAscCreatedAtDesc(userId)
                : goals.findByUserIdAndStatusOrderByTargetDateAsc(userId, status);
        return list.stream().map(this::decorateGoal).toList();
    }

    @Transactional
    public GoalResponse createGoal(UUID userId, GoalRequest req) {
        Goal goal = goals.save(Goal.builder()
                .userId(userId)
                .title(req.title().trim())
                .description(req.description())
                .category(orDefault(req.category(), "personal"))
                .icon(orDefault(req.icon(), "flag"))
                .color(orDefault(req.color(), "#111111"))
                .projectId(req.projectId())
                .targetValue(req.targetValue() == null ? BigDecimal.ONE : req.targetValue())
                .currentValue(req.currentValue() == null ? BigDecimal.ZERO : req.currentValue())
                .unit(orDefault(req.unit(), "steps"))
                .startDate(req.startDate() == null ? LocalDate.now() : req.startDate())
                .targetDate(req.targetDate())
                .status(req.status() == null ? GoalStatus.ACTIVE : req.status())
                .build());
        publishGoal(Topics.Planning.GOAL_CREATED, goal);
        return decorateGoal(goal);
    }

    @Transactional
    public GoalResponse updateGoal(UUID userId, UUID id, GoalRequest req) {
        Goal goal = goals.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Goal", id));
        BigDecimal before = goal.getCurrentValue();

        if (req.title() != null && !req.title().isBlank()) {
            goal.setTitle(req.title().trim());
        }
        goal.setDescription(req.description());
        if (req.category() != null) {
            goal.setCategory(req.category());
        }
        if (req.icon() != null) {
            goal.setIcon(req.icon());
        }
        if (req.color() != null) {
            goal.setColor(req.color());
        }
        goal.setProjectId(req.projectId());
        if (req.targetValue() != null) {
            goal.setTargetValue(req.targetValue());
        }
        if (req.currentValue() != null) {
            goal.setCurrentValue(req.currentValue());
        }
        if (req.unit() != null) {
            goal.setUnit(req.unit());
        }
        if (req.startDate() != null) {
            goal.setStartDate(req.startDate());
        }
        goal.setTargetDate(req.targetDate());
        if (req.status() != null) {
            goal.setStatus(req.status());
        }

        // Reaching the target flips the status automatically — asking the user to
        // also mark it achieved is busywork.
        if (goal.progress() >= 1.0 && goal.getStatus() == GoalStatus.ACTIVE) {
            goal.setStatus(GoalStatus.ACHIEVED);
            goal.setAchievedAt(Instant.now());
        }
        goal.setUpdatedAt(Instant.now());
        goals.save(goal);

        if (req.currentValue() != null && req.currentValue().compareTo(before) != 0) {
            publishGoal(Topics.Planning.GOAL_PROGRESSED, goal);
        }
        return decorateGoal(goal);
    }

    @Transactional
    public void deleteGoal(UUID userId, UUID id) {
        Goal goal = goals.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Goal", id));
        goals.delete(goal);
    }

    private GoalResponse decorateGoal(Goal g) {
        List<Task> linked = tasks.search(g.getUserId(), null, null, g.getId(), false, null, TaskStatus.DONE);
        long done = linked.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();

        Double elapsed = g.timeElapsed();
        String pace = elapsed == null ? "unknown"
                : g.progress() >= elapsed + 0.1 ? "ahead"
                : g.progress() <= elapsed - 0.1 ? "behind" : "on-track";

        Integer daysLeft = g.getTargetDate() == null ? null
                : (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), g.getTargetDate());

        return new GoalResponse(g.getId(), g.getTitle(), g.getDescription(), g.getCategory(),
                g.getIcon(), g.getColor(), g.getProjectId(), g.getTargetValue(), g.getCurrentValue(),
                g.getUnit(), g.getStartDate(), g.getTargetDate(), g.getStatus(),
                g.progress(), elapsed, pace, daysLeft, linked.size(), done, g.getCreatedAt());
    }

    // =============================================================== agenda
    @Transactional(readOnly = true)
    public AgendaResponse agenda(UUID userId, LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        Map<UUID, String> projectNames = projectNames(userId);

        List<Task> everything = tasks.agendaFor(userId, day, CLOSED);
        List<TaskResponse> overdue = everything.stream()
                .filter(t -> t.getDueDate() != null && t.getDueDate().isBefore(day))
                .map(t -> decorate(userId, t, projectNames)).toList();
        List<TaskResponse> dueToday = everything.stream()
                .filter(t -> day.equals(t.getDueDate()))
                .map(t -> decorate(userId, t, projectNames)).toList();
        List<TaskResponse> scheduled = everything.stream()
                .filter(t -> day.equals(t.getScheduledFor()) && !day.equals(t.getDueDate()))
                .map(t -> decorate(userId, t, projectNames)).toList();

        Long focusMinutes = focusSessions.focusMinutes(userId, day, day);
        JournalResponse journal = journals.findByUserIdAndEntryDate(userId, day)
                .map(JournalResponse::from).orElse(null);

        return new AgendaResponse(day, overdue, dueToday, scheduled,
                focusMinutes == null ? 0 : focusMinutes, journal);
    }

    // =============================================================== helpers
    private TaskResponse decorate(UUID userId, Task task, Map<UUID, String> projectNames) {
        List<TaskResponse> subtasks = tasks
                .findByUserIdAndParentTaskIdOrderBySortOrderAsc(userId, task.getId()).stream()
                .map(s -> TaskResponse.from(s, projectNames.get(s.getProjectId()), List.of()))
                .toList();
        return TaskResponse.from(task, projectNames.get(task.getProjectId()), subtasks);
    }

    private Map<UUID, String> projectNames(UUID userId) {
        Map<UUID, String> names = new HashMap<>();
        projects.findByUserIdOrderBySortOrderAscNameAsc(userId)
                .forEach(p -> names.put(p.getId(), p.getName()));
        return names;
    }

    private Task requireTask(UUID userId, UUID id) {
        return tasks.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Task", id));
    }

    private void publishTask(String type, Task task) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", task.getId().toString());
        payload.put("title", task.getTitle());
        payload.put("priority", task.getPriority().name());
        payload.put("status", task.getStatus().name());
        payload.put("dueDate", task.getDueDate() == null ? null : task.getDueDate().toString());
        payload.put("projectId", task.getProjectId() == null ? null : task.getProjectId().toString());
        payload.put("estimateMinutes", task.getEstimateMinutes());
        events.publish(Topics.PLANNING_EVENTS, DomainEvent.of(type, "Task",
                task.getId().toString(), task.getUserId(), 0L, payload));
    }

    private void publishGoal(String type, Goal goal) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("goalId", goal.getId().toString());
        payload.put("title", goal.getTitle());
        payload.put("progress", goal.progress());
        payload.put("status", goal.getStatus().name());
        events.publish(Topics.PLANNING_EVENTS, DomainEvent.of(type, "Goal",
                goal.getId().toString(), goal.getUserId(), 0L, payload));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
