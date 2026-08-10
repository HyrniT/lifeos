package com.lifeos.expense.repo;

import com.lifeos.expense.domain.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    List<Budget> findByUserIdAndActiveOrderByNameAsc(UUID userId, boolean active);

    Optional<Budget> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndActiveTrue(UUID userId);
}
