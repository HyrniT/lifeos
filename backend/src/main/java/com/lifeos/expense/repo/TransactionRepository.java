package com.lifeos.expense.repo;

import com.lifeos.expense.domain.ExpenseEnums.TxType;
import com.lifeos.expense.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    List<Transaction> findByUserIdAndOccurredOnBetweenOrderByOccurredOnDescCreatedAtDesc(
            UUID userId, LocalDate from, LocalDate to);

    /**
     * The transaction list endpoint. Every filter is optional, hence the
     * {@code :param IS NULL OR} pattern rather than a Specification — one readable
     * query beats a criteria builder nobody can review.
     */
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.userId = :userId
              AND t.occurredOn BETWEEN :from AND :to
              AND (:accountId  IS NULL OR t.accountId  = :accountId)
              AND (:categoryId IS NULL OR t.categoryId = :categoryId)
              AND (:type       IS NULL OR t.type       = :type)
              AND (:minAmount  IS NULL OR t.amount    >= :minAmount)
              AND (:maxAmount  IS NULL OR t.amount    <= :maxAmount)
              AND (:search     IS NULL OR LOWER(t.note)     LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
                                       OR LOWER(t.merchant) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%')))
            ORDER BY t.occurredOn DESC, t.createdAt DESC
            """)
    Page<Transaction> search(@Param("userId") UUID userId,
                             @Param("from") LocalDate from,
                             @Param("to") LocalDate to,
                             @Param("accountId") UUID accountId,
                             @Param("categoryId") UUID categoryId,
                             @Param("type") TxType type,
                             @Param("minAmount") BigDecimal minAmount,
                             @Param("maxAmount") BigDecimal maxAmount,
                             @Param("search") String search,
                             Pageable pageable);

    @Query("""
            SELECT t.categoryId, SUM(t.amount) FROM Transaction t
            WHERE t.userId = :userId AND t.type = :type
              AND t.occurredOn BETWEEN :from AND :to
            GROUP BY t.categoryId
            """)
    List<Object[]> sumByCategory(@Param("userId") UUID userId,
                                 @Param("type") TxType type,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);

    /** Transfers are excluded by the caller passing {@code TxType.TRANSFER} — moving
     *  money between your own accounts is not spending and must not show up in cash flow. */
    @Query("""
            SELECT t.occurredOn, t.type, SUM(t.amount) FROM Transaction t
            WHERE t.userId = :userId AND t.occurredOn BETWEEN :from AND :to
              AND t.type <> :excluded
            GROUP BY t.occurredOn, t.type
            ORDER BY t.occurredOn
            """)
    List<Object[]> dailyTotals(@Param("userId") UUID userId,
                               @Param("from") LocalDate from,
                               @Param("to") LocalDate to,
                               @Param("excluded") TxType excluded);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.userId = :userId AND t.type = :type AND t.occurredOn BETWEEN :from AND :to
            """)
    BigDecimal sumByType(@Param("userId") UUID userId,
                         @Param("type") TxType type,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.userId = :userId AND t.type = :type
              AND t.categoryId = :categoryId AND t.occurredOn BETWEEN :from AND :to
            """)
    BigDecimal spentInCategory(@Param("userId") UUID userId,
                               @Param("categoryId") UUID categoryId,
                               @Param("type") TxType type,
                               @Param("from") LocalDate from,
                               @Param("to") LocalDate to);

    @Query("SELECT t.merchant, COUNT(t), SUM(t.amount) FROM Transaction t "
            + "WHERE t.userId = :userId AND t.merchant IS NOT NULL "
            + "AND t.occurredOn BETWEEN :from AND :to "
            + "GROUP BY t.merchant ORDER BY SUM(t.amount) DESC")
    List<Object[]> topMerchants(@Param("userId") UUID userId,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to,
                                Pageable pageable);

    long countByUserId(UUID userId);

    List<Transaction> findByAccountIdOrToAccountId(UUID accountId, UUID toAccountId);
}
