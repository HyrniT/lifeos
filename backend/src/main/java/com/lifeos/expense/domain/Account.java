package com.lifeos.expense.domain;

import com.lifeos.expense.domain.ExpenseEnums.AccountType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account", schema = "expense", indexes = {
        @Index(name = "idx_account_user", columnList = "user_id,archived,sort_order")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    @Builder.Default
    private AccountType type = AccountType.CASH;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "VND";

    /**
     * Money is BigDecimal end to end. Doubles lose cents on anything with a long
     * enough transaction history, and a budgeting app that quietly drifts is worthless.
     */
    @Column(name = "opening_balance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal openingBalance = BigDecimal.ZERO;

    /** Maintained incrementally on every write so listing accounts stays a single query. */
    @Column(name = "current_balance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(length = 48)
    @Builder.Default
    private String icon = "wallet";

    @Column(length = 16)
    @Builder.Default
    private String color = "#111111";

    @Column(name = "credit_limit", precision = 19, scale = 4)
    private BigDecimal creditLimit;

    @Column(name = "exclude_from_totals", nullable = false)
    @Builder.Default
    private boolean excludeFromTotals = false;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean archived = false;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
