package com.lifeos.expense.repo;

import com.lifeos.expense.domain.Category;
import com.lifeos.expense.domain.ExpenseEnums.CategoryKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByUserIdAndArchivedOrderBySortOrderAscNameAsc(UUID userId, boolean archived);

    List<Category> findByUserIdAndKindAndArchivedOrderBySortOrderAscNameAsc(
            UUID userId, CategoryKind kind, boolean archived);

    Optional<Category> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}
