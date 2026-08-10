package com.lifeos.planning.repo;

import com.lifeos.planning.domain.Goal;
import com.lifeos.planning.domain.PlanningEnums.GoalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    List<Goal> findByUserIdOrderByTargetDateAscCreatedAtDesc(UUID userId);

    List<Goal> findByUserIdAndStatusOrderByTargetDateAsc(UUID userId, GoalStatus status);

    Optional<Goal> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndStatus(UUID userId, GoalStatus status);

    /** Working set for goal-deadline reminders. */
    List<Goal> findByStatusAndTargetDateBetween(GoalStatus status, LocalDate from, LocalDate to);
}
