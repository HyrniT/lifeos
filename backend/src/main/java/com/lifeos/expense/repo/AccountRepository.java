package com.lifeos.expense.repo;

import com.lifeos.expense.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByUserIdAndArchivedOrderBySortOrderAscNameAsc(UUID userId, boolean archived);

    List<Account> findByUserIdOrderBySortOrderAscNameAsc(UUID userId);

    Optional<Account> findByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT COALESCE(MAX(a.sortOrder), -1) FROM Account a WHERE a.userId = :userId")
    int maxSortOrder(@Param("userId") UUID userId);

    long countByUserId(UUID userId);
}
