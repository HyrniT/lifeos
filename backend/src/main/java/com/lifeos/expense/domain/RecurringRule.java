package com.lifeos.expense.domain;

import com.lifeos.expense.domain.ExpenseEnums.Cadence;
import com.lifeos.expense.domain.ExpenseEnums.TxType;
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

/**
 * Template for transactions that repeat — rent, salary, subscriptions.
 *
 * A scheduler materialises rows when {@code nextRunOn} arrives rather than
 * generating a year of future transactions up front, so editing a rule does not
 * mean rewriting history.
 */
@Entity
@Table(name = "recurring_rule", schema = "expense", indexes = {
        @Index(name = "idx_recurring_due", columnList = "active,next_run_on")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TxType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private Cadence cadence = Cadence.MONTHLY;

    @Column(name = "next_run_on", nullable = false)
    private LocalDate nextRunOn;

    @Column(name = "end_on")
    private LocalDate endOn;

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "last_run_on")
    private LocalDate lastRunOn;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    public LocalDate advance(LocalDate from) {
        return switch (cadence) {
            case DAILY -> from.plusDays(1);
            case WEEKLY -> from.plusWeeks(1);
            case BIWEEKLY -> from.plusWeeks(2);
            case MONTHLY -> from.plusMonths(1);
            case QUARTERLY -> from.plusMonths(3);
            case YEARLY -> from.plusYears(1);
        };
    }
}
