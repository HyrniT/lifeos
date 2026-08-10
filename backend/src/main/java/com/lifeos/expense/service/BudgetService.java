package com.lifeos.expense.service;

import com.lifeos.common.event.DomainEvent;
import com.lifeos.common.event.EventPublisher;
import com.lifeos.common.event.Topics;
import com.lifeos.common.exception.ApiException;
import com.lifeos.expense.domain.Budget;
import com.lifeos.expense.domain.Category;
import com.lifeos.expense.domain.ExpenseEnums.BudgetPeriod;
import com.lifeos.expense.domain.ExpenseEnums.TxType;
import com.lifeos.expense.dto.ExpenseDtos.BudgetRequest;
import com.lifeos.expense.dto.ExpenseDtos.BudgetStatus;
import com.lifeos.expense.repo.BudgetRepository;
import com.lifeos.expense.repo.CategoryRepository;
import com.lifeos.expense.repo.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Budgets and where the user stands against them.
 *
 * "Safe daily spend" is the number that actually changes behaviour: remaining
 * budget divided by days left in the period, rather than a raw percentage the user
 * has to translate themselves.
 */
@Service
public class BudgetService {

    private final BudgetRepository budgets;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final EventPublisher events;

    public BudgetService(BudgetRepository budgets, CategoryRepository categories,
                         TransactionRepository transactions, EventPublisher events) {
        this.budgets = budgets;
        this.categories = categories;
        this.transactions = transactions;
        this.events = events;
    }

    @Transactional
    public Budget create(UUID userId, BudgetRequest req) {
        return budgets.save(Budget.builder()
                .userId(userId)
                .name(req.name().trim())
                .categoryId(req.categoryId())
                .amount(req.amount())
                .period(req.period() == null ? BudgetPeriod.MONTHLY : req.period())
                .startDate(req.startDate() == null ? LocalDate.now().withDayOfMonth(1) : req.startDate())
                .rollover(Boolean.TRUE.equals(req.rollover()))
                .alertThreshold(req.alertThreshold() == null ? 80 : req.alertThreshold())
                .build());
    }

    @Transactional
    public Budget update(UUID userId, UUID id, BudgetRequest req) {
        Budget budget = budgets.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Budget", id));
        if (req.name() != null && !req.name().isBlank()) {
            budget.setName(req.name().trim());
        }
        if (req.amount() != null) {
            budget.setAmount(req.amount());
        }
        if (req.period() != null) {
            budget.setPeriod(req.period());
        }
        if (req.startDate() != null) {
            budget.setStartDate(req.startDate());
        }
        if (req.rollover() != null) {
            budget.setRollover(req.rollover());
        }
        if (req.alertThreshold() != null) {
            budget.setAlertThreshold(req.alertThreshold());
        }
        budget.setCategoryId(req.categoryId());
        return budgets.save(budget);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Budget budget = budgets.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Budget", id));
        budgets.delete(budget);
    }

    @Transactional(readOnly = true)
    public List<BudgetStatus> statuses(UUID userId, LocalDate reference) {
        LocalDate today = reference == null ? LocalDate.now() : reference;

        Map<UUID, Category> categoriesById = new HashMap<>();
        categories.findByUserIdAndArchivedOrderBySortOrderAscNameAsc(userId, false)
                .forEach(c -> categoriesById.put(c.getId(), c));

        return budgets.findByUserIdAndActiveOrderByNameAsc(userId, true).stream()
                .map(b -> toStatus(userId, b, today, categoriesById))
                .toList();
    }

    private BudgetStatus toStatus(UUID userId, Budget budget, LocalDate today, Map<UUID, Category> byId) {
        LocalDate start = periodStart(budget.getPeriod(), today);
        LocalDate end = periodEnd(budget.getPeriod(), start);

        BigDecimal spent = budget.getCategoryId() == null
                ? transactions.sumByType(userId, TxType.EXPENSE, start, end)
                : transactions.spentInCategory(userId, budget.getCategoryId(), TxType.EXPENSE, start, end);
        if (spent == null) {
            spent = BigDecimal.ZERO;
        }

        BigDecimal remaining = budget.getAmount().subtract(spent);
        double used = budget.getAmount().compareTo(BigDecimal.ZERO) == 0
                ? 0
                : spent.divide(budget.getAmount(), 4, RoundingMode.HALF_UP).doubleValue();

        int daysLeft = (int) Math.max(0, ChronoUnit.DAYS.between(today, end) + 1);
        BigDecimal safeDaily = daysLeft == 0 || remaining.compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.ZERO
                : remaining.divide(BigDecimal.valueOf(daysLeft), 2, RoundingMode.DOWN);

        String state = used >= 1.0 ? "EXCEEDED"
                : used * 100 >= budget.getAlertThreshold() ? "WARNING"
                : isAheadOfPace(used, start, end, today) ? "AHEAD" : "ON_TRACK";

        if ("EXCEEDED".equals(state)) {
            events.publish(Topics.EXPENSE_EVENTS, DomainEvent.of(
                    Topics.Expense.BUDGET_EXCEEDED, "Budget", budget.getId().toString(), userId, 0L,
                    Map.of("name", budget.getName(), "amount", budget.getAmount(), "spent", spent)));
        }

        Category category = budget.getCategoryId() == null ? null : byId.get(budget.getCategoryId());
        return new BudgetStatus(
                budget.getId(), budget.getName(), budget.getCategoryId(),
                category == null ? "All spending" : category.getName(),
                category == null ? "wallet" : category.getIcon(),
                budget.getAmount(), spent, remaining, Math.round(used * 1000) / 1000.0,
                budget.getPeriod(), start, end, daysLeft, safeDaily, state, budget.getAlertThreshold());
    }

    /**
     * Compares spend-so-far against elapsed-time-so-far. Being at 40% of the budget
     * on day 20 of 30 is "ahead"; being at 40% on day 5 is not.
     */
    private boolean isAheadOfPace(double used, LocalDate start, LocalDate end, LocalDate today) {
        long total = ChronoUnit.DAYS.between(start, end) + 1;
        long elapsed = Math.max(1, ChronoUnit.DAYS.between(start, today) + 1);
        double expected = (double) elapsed / total;
        return used < expected - 0.1;
    }

    public static LocalDate periodStart(BudgetPeriod period, LocalDate today) {
        return switch (period) {
            case WEEKLY -> today.with(DayOfWeek.MONDAY);
            case MONTHLY -> today.withDayOfMonth(1);
            case QUARTERLY -> today.withDayOfMonth(1).withMonth(((today.getMonthValue() - 1) / 3) * 3 + 1);
            case YEARLY -> today.withDayOfYear(1);
        };
    }

    public static LocalDate periodEnd(BudgetPeriod period, LocalDate start) {
        return switch (period) {
            case WEEKLY -> start.plusDays(6);
            case MONTHLY -> start.withDayOfMonth(start.lengthOfMonth());
            case QUARTERLY -> start.plusMonths(3).minusDays(1);
            case YEARLY -> start.plusYears(1).minusDays(1);
        };
    }
}
