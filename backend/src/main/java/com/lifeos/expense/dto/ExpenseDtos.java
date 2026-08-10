package com.lifeos.expense.dto;

import com.lifeos.expense.domain.*;
import com.lifeos.expense.domain.ExpenseEnums.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ExpenseDtos {

    private ExpenseDtos() {
    }

    // ============================================================== accounts
    public record AccountRequest(
            @NotBlank @Size(max = 80) String name,
            AccountType type,
            @Size(min = 3, max = 3) String currency,
            BigDecimal openingBalance,
            @Size(max = 48) String icon,
            @Size(max = 16) String color,
            BigDecimal creditLimit,
            Boolean excludeFromTotals
    ) {
    }

    public record AccountResponse(
            UUID id, String name, AccountType type, String currency,
            BigDecimal openingBalance, BigDecimal currentBalance, BigDecimal creditLimit,
            String icon, String color, boolean excludeFromTotals, int sortOrder,
            boolean archived, Instant createdAt
    ) {
        public static AccountResponse from(Account a) {
            return new AccountResponse(a.getId(), a.getName(), a.getType(), a.getCurrency(),
                    a.getOpeningBalance(), a.getCurrentBalance(), a.getCreditLimit(),
                    a.getIcon(), a.getColor(), a.isExcludeFromTotals(), a.getSortOrder(),
                    a.isArchived(), a.getCreatedAt());
        }
    }

    // ============================================================ categories
    public record CategoryRequest(
            @NotBlank @Size(max = 80) String name,
            CategoryKind kind,
            @Size(max = 48) String icon,
            @Size(max = 16) String color,
            UUID parentId,
            BigDecimal monthlyBudget
    ) {
    }

    public record CategoryResponse(
            UUID id, String name, CategoryKind kind, String icon, String color,
            UUID parentId, BigDecimal monthlyBudget, int sortOrder, boolean archived, boolean system
    ) {
        public static CategoryResponse from(Category c) {
            return new CategoryResponse(c.getId(), c.getName(), c.getKind(), c.getIcon(), c.getColor(),
                    c.getParentId(), c.getMonthlyBudget(), c.getSortOrder(), c.isArchived(), c.isSystem());
        }
    }

    // ========================================================== transactions
    public record TransactionRequest(
            @NotNull UUID accountId,
            UUID toAccountId,
            UUID categoryId,
            @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "Amount must be greater than zero")
            BigDecimal amount,
            @Size(min = 3, max = 3) String currency,
            @NotNull TxType type,
            LocalDate occurredOn,
            @Size(max = 500) String note,
            @Size(max = 120) String merchant,
            Set<String> tags
    ) {
    }

    public record TransactionResponse(
            UUID id, UUID accountId, String accountName, UUID toAccountId, String toAccountName,
            UUID categoryId, String categoryName, String categoryIcon, String categoryColor,
            BigDecimal amount, BigDecimal signedAmount, String currency, TxType type,
            LocalDate occurredOn, String note, String merchant, Set<String> tags,
            boolean recurring, Instant createdAt
    ) {
    }

    // =============================================================== budgets
    public record BudgetRequest(
            @NotBlank @Size(max = 80) String name,
            UUID categoryId,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
            BudgetPeriod period,
            LocalDate startDate,
            Boolean rollover,
            @Min(1) @Max(100) Integer alertThreshold
    ) {
    }

    /** A budget plus where the user currently stands against it. */
    public record BudgetStatus(
            UUID id, String name, UUID categoryId, String categoryName, String categoryIcon,
            BigDecimal amount, BigDecimal spent, BigDecimal remaining, double usedRatio,
            BudgetPeriod period, LocalDate periodStart, LocalDate periodEnd,
            int daysLeft, BigDecimal safeDailySpend, String state, int alertThreshold
    ) {
    }

    // ============================================================= recurring
    public record RecurringRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull UUID accountId,
            UUID categoryId,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
            @Size(min = 3, max = 3) String currency,
            @NotNull TxType type,
            Cadence cadence,
            @NotNull LocalDate nextRunOn,
            LocalDate endOn,
            @Size(max = 500) String note
    ) {
    }

    public record RecurringResponse(
            UUID id, String name, UUID accountId, UUID categoryId, BigDecimal amount,
            String currency, TxType type, Cadence cadence, LocalDate nextRunOn,
            LocalDate endOn, LocalDate lastRunOn, String note, boolean active
    ) {
        public static RecurringResponse from(RecurringRule r) {
            return new RecurringResponse(r.getId(), r.getName(), r.getAccountId(), r.getCategoryId(),
                    r.getAmount(), r.getCurrency(), r.getType(), r.getCadence(), r.getNextRunOn(),
                    r.getEndOn(), r.getLastRunOn(), r.getNote(), r.isActive());
        }
    }

    // ============================================================ statistics
    public record MoneyOverview(
            LocalDate from,
            LocalDate to,
            String currency,
            BigDecimal totalIncome,
            BigDecimal totalExpense,
            BigDecimal net,
            BigDecimal netWorth,
            BigDecimal liquidBalance,
            double savingsRate,
            BigDecimal averageDailySpend,
            BigDecimal projectedMonthEnd,
            long transactionCount,
            BigDecimal biggestExpense,
            String topCategory,
            double changeVsPreviousPeriod
    ) {
    }

    public record CategoryBreakdown(
            UUID categoryId, String name, String icon, String color,
            BigDecimal amount, double share, long transactionCount, BigDecimal averageAmount
    ) {
    }

    /** One point on the cash-flow chart. */
    public record CashFlowPoint(
            LocalDate date, BigDecimal income, BigDecimal expense, BigDecimal net, BigDecimal runningBalance
    ) {
    }

    public record MerchantSpend(String merchant, long count, BigDecimal total) {
    }

    public record TrendPoint(String label, BigDecimal income, BigDecimal expense, BigDecimal net) {
    }

    public record SpendingInsight(String code, String severity, String title, String message, Map<String, Object> data) {
    }

    public record ExpenseStatistics(
            MoneyOverview overview,
            List<CategoryBreakdown> byCategory,
            List<CashFlowPoint> cashFlow,
            List<TrendPoint> monthlyTrend,
            List<MerchantSpend> topMerchants,
            List<BudgetStatus> budgets,
            List<SpendingInsight> insights,
            Map<String, BigDecimal> weekdayPattern
    ) {
    }
}
