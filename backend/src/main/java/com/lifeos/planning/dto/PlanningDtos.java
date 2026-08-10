package com.lifeos.planning.dto;

import com.lifeos.planning.domain.*;
import com.lifeos.planning.domain.PlanningEnums.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlanningDtos {

    private PlanningDtos() {
    }

    // ================================================================= tasks
    public record TaskRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String notes,
            UUID projectId,
            UUID goalId,
            UUID parentTaskId,
            Priority priority,
            TaskStatus status,
            LocalDate dueDate,
            LocalTime dueTime,
            LocalDate scheduledFor,
            @Min(1) @Max(1440) Integer estimateMinutes,
            Recurrence recurrence,
            Set<String> tags
    ) {
    }

    public record TaskResponse(
            UUID id, String title, String notes, UUID projectId, String projectName,
            UUID goalId, UUID parentTaskId, Priority priority, TaskStatus status,
            LocalDate dueDate, LocalTime dueTime, LocalDate scheduledFor,
            Integer estimateMinutes, int actualMinutes, Recurrence recurrence,
            Set<String> tags, int sortOrder, int eisenhowerQuadrant, boolean overdue,
            Instant completedAt, Instant createdAt, List<TaskResponse> subtasks,
            int subtaskCount, int subtasksDone
    ) {
        public static TaskResponse from(Task t, String projectName, List<TaskResponse> subtasks) {
            int done = (int) subtasks.stream().filter(s -> s.status() == TaskStatus.DONE).count();
            return new TaskResponse(t.getId(), t.getTitle(), t.getNotes(), t.getProjectId(), projectName,
                    t.getGoalId(), t.getParentTaskId(), t.getPriority(), t.getStatus(),
                    t.getDueDate(), t.getDueTime(), t.getScheduledFor(), t.getEstimateMinutes(),
                    t.getActualMinutes(), t.getRecurrence(), t.getTags(), t.getSortOrder(),
                    t.eisenhowerQuadrant(), t.isOverdue(), t.getCompletedAt(), t.getCreatedAt(),
                    subtasks, subtasks.size(), done);
        }
    }

    public record ReorderRequest(List<UUID> orderedIds) {
    }

    // ================================================================= goals
    public record GoalRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            String category,
            String icon,
            String color,
            UUID projectId,
            BigDecimal targetValue,
            BigDecimal currentValue,
            String unit,
            LocalDate startDate,
            LocalDate targetDate,
            GoalStatus status
    ) {
    }

    public record GoalResponse(
            UUID id, String title, String description, String category, String icon, String color,
            UUID projectId, BigDecimal targetValue, BigDecimal currentValue, String unit,
            LocalDate startDate, LocalDate targetDate, GoalStatus status,
            double progress, Double timeElapsed, String pace, Integer daysRemaining,
            long linkedTasks, long linkedTasksDone, Instant createdAt
    ) {
    }

    // ============================================================== projects
    public record ProjectRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description,
            String icon,
            String color,
            ProjectStatus status,
            LocalDate dueDate
    ) {
    }

    public record ProjectResponse(
            UUID id, String name, String description, String icon, String color,
            ProjectStatus status, LocalDate dueDate, int sortOrder,
            long taskCount, long taskDone, double progress, Instant createdAt
    ) {
    }

    // ================================================================ focus
    public record StartFocusRequest(UUID taskId, SessionType type, @Min(1) @Max(240) Integer plannedMinutes) {
    }

    public record EndFocusRequest(@Min(1) @Max(5) Integer focusScore, @Size(max = 500) String note,
                                  Boolean completed) {
    }

    public record FocusResponse(
            UUID id, UUID taskId, SessionType type, LocalDate sessionDate,
            Instant startedAt, Instant endedAt, int plannedMinutes, int actualMinutes,
            boolean completed, Integer focusScore, String note
    ) {
        public static FocusResponse from(FocusSession s) {
            return new FocusResponse(s.getId(), s.getTaskId(), s.getType(), s.getSessionDate(),
                    s.getStartedAt(), s.getEndedAt(), s.getPlannedMinutes(), s.getActualMinutes(),
                    s.isCompleted(), s.getFocusScore(), s.getNote());
        }
    }

    // ============================================================== journal
    public record JournalRequest(
            LocalDate entryDate,
            @Min(1) @Max(5) Integer mood,
            @Min(1) @Max(5) Integer energy,
            @Size(max = 1000) String highlights,
            @Size(max = 1000) String gratitude,
            @Size(max = 4000) String notes
    ) {
    }

    public record JournalResponse(
            UUID id, LocalDate entryDate, Integer mood, Integer energy,
            String highlights, String gratitude, String notes, Instant updatedAt
    ) {
        public static JournalResponse from(JournalEntry e) {
            return new JournalResponse(e.getId(), e.getEntryDate(), e.getMood(), e.getEnergy(),
                    e.getHighlights(), e.getGratitude(), e.getNotes(), e.getUpdatedAt());
        }
    }

    // =========================================================== statistics
    public record ProductivityPoint(LocalDate date, long completed, long created, long focusMinutes) {
    }

    public record PlanningStatistics(
            long tasksOpen,
            long tasksDone,
            long tasksOverdue,
            long tasksCompletedLast7d,
            long tasksCompletedLast30d,
            double completionRate30d,
            long focusMinutesLast7d,
            long focusMinutesLast30d,
            long focusSessionsTotal,
            double averageSessionMinutes,
            long activeGoals,
            long achievedGoals,
            double averageGoalProgress,
            Map<String, Long> byQuadrant,
            Map<String, Long> byPriority,
            List<ProductivityPoint> timeline,
            Map<String, Long> focusByHour,
            String mostProductiveDay,
            Double averageMood,
            Double averageEnergy
    ) {
    }

    public record AgendaResponse(
            LocalDate date,
            List<TaskResponse> overdue,
            List<TaskResponse> dueToday,
            List<TaskResponse> scheduled,
            long focusMinutesToday,
            JournalResponse journal
    ) {
    }
}
