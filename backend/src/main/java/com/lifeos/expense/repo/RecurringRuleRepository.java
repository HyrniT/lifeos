package com.lifeos.expense.repo;

import com.lifeos.expense.domain.RecurringRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecurringRuleRepository extends JpaRepository<RecurringRule, UUID> {

    List<RecurringRule> findByUserIdOrderByNextRunOnAsc(UUID userId);

    Optional<RecurringRule> findByIdAndUserId(UUID id, UUID userId);

    /** Picked up by the scheduler that materialises due transactions. */
    List<RecurringRule> findByActiveTrueAndNextRunOnLessThanEqual(LocalDate date);
}
