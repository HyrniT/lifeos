package com.lifeos.planning.domain;

import com.lifeos.planning.domain.PlanningEnums.Priority;
import com.lifeos.planning.domain.PlanningEnums.Recurrence;
import com.lifeos.planning.domain.PlanningEnums.TaskStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "task", schema = "planning", indexes = {
        @Index(name = "idx_task_user_status", columnList = "user_id,status,due_date"),
        @Index(name = "idx_task_user_due", columnList = "user_id,due_date"),
        @Index(name = "idx_task_project", columnList = "project_id"),
        @Index(name = "idx_task_parent", columnList = "parent_task_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String notes;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "goal_id")
    private UUID goalId;

    /** Set on sub-tasks; a task with children is a checklist. */
    @Column(name = "parent_task_id")
    private UUID parentTaskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    @Builder.Default
    private Priority priority = Priority.P3;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private TaskStatus status = TaskStatus.TODO;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "due_time")
    private LocalTime dueTime;

    /** When the user intends to work on it, which is often not when it is due. */
    @Column(name = "scheduled_for")
    private LocalDate scheduledFor;

    @Column(name = "estimate_minutes")
    private Integer estimateMinutes;

    @Column(name = "actual_minutes", nullable = false)
    @Builder.Default
    private int actualMinutes = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private Recurrence recurrence = Recurrence.NONE;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "task_tags", schema = "planning", joinColumns = @JoinColumn(name = "task_id"))
    @Column(name = "tag", length = 40)
    @Builder.Default
    private Set<String> tags = new LinkedHashSet<>();

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "completed_at")
    private Instant completedAt;


    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    /**
     * Eisenhower quadrant, derived rather than stored so it can never disagree with
     * the priority and due date it is computed from.
     * 1 urgent+important, 2 important, 3 urgent, 4 neither.
     */
    public int eisenhowerQuadrant() {
        boolean important = priority == Priority.P1 || priority == Priority.P2;
        boolean urgent = dueDate != null && !dueDate.isAfter(LocalDate.now().plusDays(1));
        if (important && urgent) {
            return 1;
        }
        if (important) {
            return 2;
        }
        return urgent ? 3 : 4;
    }

    public boolean isOverdue() {
        return status != TaskStatus.DONE && status != TaskStatus.CANCELLED
                && dueDate != null && dueDate.isBefore(LocalDate.now());
    }

    public LocalDate nextOccurrence() {
        LocalDate base = dueDate == null ? LocalDate.now() : dueDate;
        return switch (recurrence) {
            case NONE -> null;
            case DAILY -> base.plusDays(1);
            case WEEKDAYS -> {
                LocalDate next = base.plusDays(1);
                while (next.getDayOfWeek().getValue() > 5) {
                    next = next.plusDays(1);
                }
                yield next;
            }
            case WEEKLY -> base.plusWeeks(1);
            case BIWEEKLY -> base.plusWeeks(2);
            case MONTHLY -> base.plusMonths(1);
            case YEARLY -> base.plusYears(1);
        };
    }
}
