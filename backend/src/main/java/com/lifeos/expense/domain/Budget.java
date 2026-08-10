package com.lifeos.expense.domain;

import com.lifeos.expense.domain.ExpenseEnums.BudgetPeriod;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "budget", schema = "expense", indexes = {
        @Index(name = "idx_budget_user", columnList = "user_id,active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Null means "everything" — the overall spending cap. */
    @Column(name = "category_id")
    private UUID categoryId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private BudgetPeriod period = BudgetPeriod.MONTHLY;

    @Column(name = "start_date", nullable = false)
    @Builder.Default
    private LocalDate startDate = LocalDate.now().withDayOfMonth(1);

    /** Carry an underspend into the next period instead of losing it. */
    @Column(nullable = false)
    @Builder.Default
    private boolean rollover = false;

    /** Percentage of the budget at which the user gets warned. */
    @Column(name = "alert_threshold", nullable = false)
    @Builder.Default
    private int alertThreshold = 80;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
