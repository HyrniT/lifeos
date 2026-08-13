package com.lifeos.expense.service;

import com.lifeos.expense.domain.Account;
import com.lifeos.expense.domain.Category;
import com.lifeos.expense.domain.ExpenseEnums.TxType;
import com.lifeos.expense.dto.ExpenseDtos.*;
import com.lifeos.expense.repo.AccountRepository;
import com.lifeos.expense.repo.CategoryRepository;
import com.lifeos.expense.repo.TransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * The money analytics the dashboard is built on.
 *
 * Two rules run through all of it: transfers never count as income or expense, and
 * every derived number is rounded once at the end rather than at each step — the
 * difference shows up as visibly wrong totals otherwise.
 */
@Service
public class StatisticsService {

    private static final int SCALE = 2;

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final BudgetService budgetService;

    public StatisticsService(TransactionRepository transactions, AccountRepository accounts,
                             CategoryRepository categories, BudgetService budgetService) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.categories = categories;
        this.budgetService = budgetService;
    }

    @Transactional(readOnly = true)
    public ExpenseStatistics compute(UUID userId, LocalDate from, LocalDate to) {
        MoneyOverview overview = overview(userId, from, to);
        List<CategoryBreakdown> byCategory = categoryBreakdown(userId, from, to, TxType.EXPENSE);
        List<CashFlowPoint> cashFlow = cashFlow(userId, from, to);
        List<TrendPoint> trend = monthlyTrend(userId, 12);
        List<MerchantSpend> merchants = topMerchants(userId, from, to, 8);
        List<BudgetStatus> budgets = budgetService.statuses(userId, to);
        Map<String, BigDecimal> weekday = weekdayPattern(userId, from, to);

        return new ExpenseStatistics(overview, byCategory, cashFlow, trend, merchants, budgets,
                insights(overview, byCategory, budgets, trend), weekday);
    }

    // ============================================================== overview
    @Transactional(readOnly = true)
    public MoneyOverview overview(UUID userId, LocalDate from, LocalDate to) {
        BigDecimal income = zeroIfNull(transactions.sumByType(userId, TxType.INCOME, from, to));
        BigDecimal expense = zeroIfNull(transactions.sumByType(userId, TxType.EXPENSE, from, to));
        BigDecimal net = income.subtract(expense);

        List<Account> allAccounts = accounts.findByUserIdOrderBySortOrderAscNameAsc(userId);
        BigDecimal netWorth = allAccounts.stream()
                .filter(a -> !a.isExcludeFromTotals())
                .map(Account::getCurrentBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal liquid = allAccounts.stream()
                .filter(a -> !a.isExcludeFromTotals() && !a.isArchived())
                .filter(a -> switch (a.getType()) {
                    case CASH, BANK, E_WALLET, SAVINGS -> true;
                    default -> false;
                })
                .map(Account::getCurrentBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long days = Math.max(1, ChronoUnit.DAYS.between(from, to) + 1);
        BigDecimal avgDaily = expense.divide(BigDecimal.valueOf(days), SCALE, RoundingMode.HALF_UP);

        // Project the current month by extrapolating today's daily rate — a naive
        // model, but an honest one that users can sanity-check against their own maths.
        LocalDate today = LocalDate.now();
        int daysInMonth = YearMonth.from(today).lengthOfMonth();
        BigDecimal projected = avgDaily.multiply(BigDecimal.valueOf(daysInMonth))
                .setScale(SCALE, RoundingMode.HALF_UP);

        double savingsRate = income.compareTo(BigDecimal.ZERO) <= 0
                ? 0.0
                : net.divide(income, 4, RoundingMode.HALF_UP).doubleValue();

        // Same-length window immediately before this one, for the trend arrow.
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(days - 1);
        BigDecimal prevExpense = zeroIfNull(transactions.sumByType(userId, TxType.EXPENSE, prevFrom, prevTo));
        double change = prevExpense.compareTo(BigDecimal.ZERO) == 0
                ? 0.0
                : expense.subtract(prevExpense).divide(prevExpense, 4, RoundingMode.HALF_UP).doubleValue();

        List<CategoryBreakdown> breakdown = categoryBreakdown(userId, from, to, TxType.EXPENSE);
        String topCategory = breakdown.isEmpty() ? null : breakdown.get(0).name();
        BigDecimal biggest = breakdown.isEmpty() ? BigDecimal.ZERO : breakdown.get(0).amount();

        String currency = allAccounts.isEmpty() ? "VND" : allAccounts.get(0).getCurrency();

        return new MoneyOverview(from, to, currency,
                scale(income), scale(expense), scale(net), scale(netWorth), scale(liquid),
                Math.round(savingsRate * 1000) / 1000.0, avgDaily, projected,
                transactions.countByUserId(userId), scale(biggest), topCategory,
                Math.round(change * 1000) / 1000.0);
    }

    // ============================================================ breakdown
    @Transactional(readOnly = true)
    public List<CategoryBreakdown> categoryBreakdown(UUID userId, LocalDate from, LocalDate to, TxType type) {
        Map<UUID, Category> byId = new HashMap<>();
        categories.findByUserIdAndArchivedOrderBySortOrderAscNameAsc(userId, false)
                .forEach(c -> byId.put(c.getId(), c));
        categories.findByUserIdAndArchivedOrderBySortOrderAscNameAsc(userId, true)
                .forEach(c -> byId.putIfAbsent(c.getId(), c));

        List<Object[]> rows = transactions.sumByCategory(userId, type, from, to);
        BigDecimal total = rows.stream()
                .map(r -> (BigDecimal) r[1])
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CategoryBreakdown> result = new ArrayList<>();
        for (Object[] row : rows) {
            UUID categoryId = (UUID) row[0];
            BigDecimal amount = zeroIfNull((BigDecimal) row[1]);
            Category category = categoryId == null ? null : byId.get(categoryId);

            double share = total.compareTo(BigDecimal.ZERO) == 0
                    ? 0 : amount.divide(total, 4, RoundingMode.HALF_UP).doubleValue();

            result.add(new CategoryBreakdown(
                    categoryId,
                    category == null ? "Uncategorised" : category.getName(),
                    category == null ? "circle-dashed" : category.getIcon(),
                    category == null ? "#8a8a8a" : category.getColor(),
                    scale(amount), Math.round(share * 1000) / 1000.0, 0L, BigDecimal.ZERO));
        }
        result.sort((a, b) -> b.amount().compareTo(a.amount()));
        return result;
    }

    // ============================================================= cash flow
    @Transactional(readOnly = true)
    public List<CashFlowPoint> cashFlow(UUID userId, LocalDate from, LocalDate to) {
        Map<LocalDate, BigDecimal[]> perDay = new TreeMap<>();
        for (Object[] row : transactions.dailyTotals(userId, from, to, TxType.TRANSFER)) {
            LocalDate date = (LocalDate) row[0];
            TxType type = (TxType) row[1];
            BigDecimal amount = zeroIfNull((BigDecimal) row[2]);
            BigDecimal[] pair = perDay.computeIfAbsent(date, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if (type == TxType.INCOME) {
                pair[0] = pair[0].add(amount);
            } else {
                pair[1] = pair[1].add(amount);
            }
        }

        List<CashFlowPoint> points = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            BigDecimal[] pair = perDay.getOrDefault(d, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal net = pair[0].subtract(pair[1]);
            running = running.add(net);
            points.add(new CashFlowPoint(d, scale(pair[0]), scale(pair[1]), scale(net), scale(running)));
        }
        return points;
    }

    // ================================================================ trends
    @Transactional(readOnly = true)
    public List<TrendPoint> monthlyTrend(UUID userId, int months) {
        List<TrendPoint> points = new ArrayList<>();
        YearMonth cursor = YearMonth.now().minusMonths(months - 1L);

        for (int i = 0; i < months; i++) {
            LocalDate start = cursor.atDay(1);
            LocalDate end = cursor.atEndOfMonth();
            BigDecimal income = zeroIfNull(transactions.sumByType(userId, TxType.INCOME, start, end));
            BigDecimal expense = zeroIfNull(transactions.sumByType(userId, TxType.EXPENSE, start, end));
            points.add(new TrendPoint(cursor.toString(), scale(income), scale(expense),
                    scale(income.subtract(expense))));
            cursor = cursor.plusMonths(1);
        }
        return points;
    }

    @Transactional(readOnly = true)
    public List<MerchantSpend> topMerchants(UUID userId, LocalDate from, LocalDate to, int limit) {
        return transactions.topMerchants(userId, from, to, PageRequest.of(0, limit)).stream()
                .map(row -> new MerchantSpend((String) row[0], ((Number) row[1]).longValue(),
                        scale(zeroIfNull((BigDecimal) row[2]))))
                .toList();
    }

    /** Which weekdays money actually leaves the account — usually a surprise to people. */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> weekdayPattern(UUID userId, LocalDate from, LocalDate to) {
        Map<DayOfWeek, BigDecimal> totals = new EnumMap<>(DayOfWeek.class);
        for (Object[] row : transactions.dailyTotals(userId, from, to, TxType.TRANSFER)) {
            if (row[1] != TxType.EXPENSE) {
                continue;
            }
            LocalDate date = (LocalDate) row[0];
            totals.merge(date.getDayOfWeek(), zeroIfNull((BigDecimal) row[2]), BigDecimal::add);
        }

        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            result.put(day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    scale(totals.getOrDefault(day, BigDecimal.ZERO)));
        }
        return result;
    }

    // ============================================================== insights
    /**
     * Turns the numbers into sentences. Each rule has a threshold chosen to fire
     * rarely — an insight panel that always shows six warnings gets ignored.
     */
    private List<SpendingInsight> insights(MoneyOverview overview, List<CategoryBreakdown> byCategory,
                                           List<BudgetStatus> budgets, List<TrendPoint> trend) {
        List<SpendingInsight> insights = new ArrayList<>();

        if (overview.savingsRate() >= 0.2) {
            insights.add(new SpendingInsight("HEALTHY_SAVINGS", "positive", "Strong savings rate",
                    "You kept %.0f%% of what you earned this period.".formatted(overview.savingsRate() * 100),
                    Map.of("savingsRate", overview.savingsRate())));
        } else if (overview.net().compareTo(BigDecimal.ZERO) < 0) {
            insights.add(new SpendingInsight("NEGATIVE_CASHFLOW", "critical", "Spending exceeded income",
                    "You spent %s more than you earned in this period."
                            .formatted(overview.net().abs()),
                    Map.of("net", overview.net())));
        }

        if (overview.changeVsPreviousPeriod() > 0.25) {
            insights.add(new SpendingInsight("SPEND_SPIKE", "warning", "Spending is up sharply",
                    "Expenses rose %.0f%% versus the previous period."
                            .formatted(overview.changeVsPreviousPeriod() * 100),
                    Map.of("change", overview.changeVsPreviousPeriod())));
        } else if (overview.changeVsPreviousPeriod() < -0.15) {
            insights.add(new SpendingInsight("SPEND_DROP", "positive", "Spending is down",
                    "Expenses fell %.0f%% versus the previous period."
                            .formatted(Math.abs(overview.changeVsPreviousPeriod()) * 100),
                    Map.of("change", overview.changeVsPreviousPeriod())));
        }

        if (!byCategory.isEmpty() && byCategory.get(0).share() > 0.4) {
            CategoryBreakdown top = byCategory.get(0);
            insights.add(new SpendingInsight("CONCENTRATED_SPEND", "info", "One category dominates",
                    "%s alone is %.0f%% of your spending.".formatted(top.name(), top.share() * 100),
                    Map.of("category", top.name(), "share", top.share())));
        }

        budgets.stream().filter(b -> "EXCEEDED".equals(b.state())).findFirst().ifPresent(b ->
                insights.add(new SpendingInsight("BUDGET_EXCEEDED", "critical", "Budget exceeded",
                        "\"%s\" is over by %s with %d day(s) left."
                                .formatted(b.name(), b.remaining().abs(), b.daysLeft()),
                        Map.of("budget", b.name(), "over", b.remaining().abs()))));

        budgets.stream().filter(b -> "WARNING".equals(b.state())).findFirst().ifPresent(b ->
                insights.add(new SpendingInsight("BUDGET_WARNING", "warning", "Budget nearly used",
                        "\"%s\" is at %.0f%%. You can spend about %s a day for the rest of the period."
                                .formatted(b.name(), b.usedRatio() * 100, b.safeDailySpend()),
                        Map.of("budget", b.name(), "used", b.usedRatio()))));

        // A rising three-month expense line matters more than any single month.
        if (trend.size() >= 3) {
            List<TrendPoint> last3 = trend.subList(trend.size() - 3, trend.size());
            boolean rising = last3.get(0).expense().compareTo(last3.get(1).expense()) < 0
                    && last3.get(1).expense().compareTo(last3.get(2).expense()) < 0;
            if (rising) {
                insights.add(new SpendingInsight("RISING_TREND", "warning", "Three months of growth",
                        "Your monthly spending has increased three months running.",
                        Map.of("months", last3.stream().map(TrendPoint::label).toList())));
            }
        }

        if (insights.isEmpty()) {
            insights.add(new SpendingInsight("STEADY", "info", "Everything looks steady",
                    "No unusual movement in this period.", Map.of()));
        }
        return insights;
    }

    // =============================================================== helpers
    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal scale(BigDecimal value) {
        return zeroIfNull(value).setScale(SCALE, RoundingMode.HALF_UP);
    }
}
