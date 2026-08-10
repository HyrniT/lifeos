package com.lifeos.planning.repo;

import com.lifeos.planning.domain.PlanningEnums.TaskStatus;
import com.lifeos.planning.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Two conventions worth knowing before editing the queries here.
 *
 * Statuses are bind parameters rather than HQL enum literals: literals of a nested
 * enum type are fragile across Hibernate versions, and a parameter reads no worse.
 *
 * Nullable string parameters are wrapped in {@code CAST(:param AS String)}. Without
 * it, PostgreSQL infers the type of a null bind as {@code bytea} and the whole query
 * dies with "function lower(bytea) does not exist" — but only when the filter is
 * omitted, which is exactly the case a quick test tends to miss.
 */
public interface TaskRepository extends JpaRepository<Task, UUID> {

    Optional<Task> findByIdAndUserId(UUID id, UUID userId);

    List<Task> findByUserIdAndParentTaskIdOrderBySortOrderAsc(UUID userId, UUID parentTaskId);

    @Query("""
            SELECT t FROM Task t
            WHERE t.userId = :userId
              AND (:status    IS NULL OR t.status    = :status)
              AND (:projectId IS NULL OR t.projectId = :projectId)
              AND (:goalId    IS NULL OR t.goalId    = :goalId)
              AND (:onlyRoot  = false  OR t.parentTaskId IS NULL)
              AND (:search    IS NULL OR LOWER(t.title) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%')))
            ORDER BY
              CASE WHEN t.status = :doneStatus THEN 1 ELSE 0 END,
              t.sortOrder ASC, t.dueDate ASC NULLS LAST, t.priority ASC, t.createdAt DESC
            """)
    List<Task> search(@Param("userId") UUID userId,
                      @Param("status") TaskStatus status,
                      @Param("projectId") UUID projectId,
                      @Param("goalId") UUID goalId,
                      @Param("onlyRoot") boolean onlyRoot,
                      @Param("search") String search,
                      @Param("doneStatus") TaskStatus doneStatus);

    /** Everything on the plate for a given day: due, scheduled, or already overdue. */
    @Query("""
            SELECT t FROM Task t
            WHERE t.userId = :userId
              AND t.status NOT IN :closedStatuses
              AND (t.dueDate <= :date OR t.scheduledFor = :date)
            ORDER BY t.priority ASC, t.dueDate ASC NULLS LAST, t.sortOrder ASC
            """)
    List<Task> agendaFor(@Param("userId") UUID userId,
                         @Param("date") LocalDate date,
                         @Param("closedStatuses") Collection<TaskStatus> closedStatuses);

    List<Task> findByUserIdAndDueDateBetween(UUID userId, LocalDate from, LocalDate to);

    long countByUserIdAndStatus(UUID userId, TaskStatus status);

    @Query("""
            SELECT t FROM Task t
            WHERE t.userId = :userId AND t.status = :doneStatus AND t.completedAt >= :since
            """)
    List<Task> completedSince(@Param("userId") UUID userId,
                              @Param("since") Instant since,
                              @Param("doneStatus") TaskStatus doneStatus);

    /**
     * The reminder scheduler's working set: open tasks whose deadline falls in a
     * bounded window around now.
     *
     * Bounded because the window has to cover the longest lead time (a week) plus
     * the overdue-nudge tail, and nothing else — scanning every task a user has
     * ever created, every few minutes, is how a scheduler quietly becomes the most
     * expensive query in the system.
     */
    @Query("""
            SELECT t FROM Task t
            WHERE t.status NOT IN :closedStatuses
              AND t.dueDate BETWEEN :from AND :to
            """)
    List<Task> withDeadlineBetween(@Param("from") LocalDate from,
                                   @Param("to") LocalDate to,
                                   @Param("closedStatuses") Collection<TaskStatus> closedStatuses);

    /** Everything still open and due on a given day — the daily summary's content. */
    @Query("""
            SELECT t FROM Task t
            WHERE t.userId = :userId
              AND t.status NOT IN :closedStatuses
              AND t.dueDate <= :date
            ORDER BY t.dueDate ASC, t.priority ASC
            """)
    List<Task> openThrough(@Param("userId") UUID userId,
                           @Param("date") LocalDate date,
                           @Param("closedStatuses") Collection<TaskStatus> closedStatuses);

    @Query("SELECT COALESCE(MAX(t.sortOrder), -1) FROM Task t WHERE t.userId = :userId")
    int maxSortOrder(@Param("userId") UUID userId);

    long countByProjectIdAndStatus(UUID projectId, TaskStatus status);

    long countByProjectId(UUID projectId);

    void deleteByParentTaskId(UUID parentTaskId);
}
