package com.lifeos.expense.service;

import com.lifeos.common.event.DomainEvent;
import com.lifeos.common.event.EventPublisher;
import com.lifeos.common.event.Topics;
import com.lifeos.common.api.Money;
import com.lifeos.common.exception.ApiException;
import com.lifeos.expense.domain.*;
import com.lifeos.expense.domain.ExpenseEnums.*;
import com.lifeos.expense.dto.ExpenseDtos.*;
import com.lifeos.expense.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Accounts, categories and the transaction ledger.
 *
 * Account balances are maintained incrementally rather than summed on read: with
 * years of history a SUM over every row on each dashboard load is the first thing
 * that gets slow, and every write already knows its own delta.
 */
@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final EventPublisher events;

    public LedgerService(AccountRepository accounts, CategoryRepository categories,
                         TransactionRepository transactions, EventPublisher events) {
        this.accounts = accounts;
        this.categories = categories;
        this.transactions = transactions;
        this.events = events;
    }

    // ============================================================== accounts
    @Transactional(readOnly = true)
    public List<AccountResponse> listAccounts(UUID userId, boolean includeArchived) {
        List<Account> list = includeArchived
                ? accounts.findByUserIdOrderBySortOrderAscNameAsc(userId)
                : accounts.findByUserIdAndArchivedOrderBySortOrderAscNameAsc(userId, false);
        return list.stream().map(AccountResponse::from).toList();
    }

    @Transactional
    public AccountResponse createAccount(UUID userId, AccountRequest req) {
        BigDecimal opening = req.openingBalance() == null ? BigDecimal.ZERO : req.openingBalance();
        Account account = accounts.save(Account.builder()
                .userId(userId)
                .name(req.name().trim())
                .type(req.type() == null ? AccountType.CASH : req.type())
                .currency(currencyOr(req.currency()))
                .openingBalance(opening)
                .currentBalance(opening)
                .icon(orDefault(req.icon(), "wallet"))
                .color(orDefault(req.color(), "#111111"))
                .creditLimit(req.creditLimit())
                .excludeFromTotals(Boolean.TRUE.equals(req.excludeFromTotals()))
                .sortOrder(accounts.maxSortOrder(userId) + 1)
                .build());
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse updateAccount(UUID userId, UUID id, AccountRequest req) {
        Account account = requireAccount(userId, id);
        if (req.name() != null && !req.name().isBlank()) {
            account.setName(req.name().trim());
        }
        if (req.type() != null) {
            account.setType(req.type());
        }
        if (req.currency() != null) {
            account.setCurrency(currencyOr(req.currency()));
        }
        if (req.icon() != null) {
            account.setIcon(req.icon());
        }
        if (req.color() != null) {
            account.setColor(req.color());
        }
        if (req.creditLimit() != null) {
            account.setCreditLimit(req.creditLimit());
        }
        if (req.excludeFromTotals() != null) {
            account.setExcludeFromTotals(req.excludeFromTotals());
        }
        if (req.openingBalance() != null && req.openingBalance().compareTo(account.getOpeningBalance()) != 0) {
            // Shift the current balance by the same delta so history stays consistent.
            BigDecimal delta = req.openingBalance().subtract(account.getOpeningBalance());
            account.setOpeningBalance(req.openingBalance());
            account.setCurrentBalance(account.getCurrentBalance().add(delta));
        }
        account.setUpdatedAt(Instant.now());
        return AccountResponse.from(accounts.save(account));
    }

    @Transactional
    public void archiveAccount(UUID userId, UUID id, boolean archived) {
        Account account = requireAccount(userId, id);
        account.setArchived(archived);
        account.setUpdatedAt(Instant.now());
        accounts.save(account);
    }

    @Transactional
    public void deleteAccount(UUID userId, UUID id) {
        Account account = requireAccount(userId, id);
        List<Transaction> linked = transactions.findByAccountIdOrToAccountId(id, id);
        if (!linked.isEmpty()) {
            throw ApiException.conflict(
                    "This account still has %d transaction(s). Archive it instead of deleting."
                            .formatted(linked.size()));
        }
        accounts.delete(account);
    }

    // ============================================================ categories
    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories(UUID userId, CategoryKind kind) {
        List<Category> list = kind == null
                ? categories.findByUserIdAndArchivedOrderBySortOrderAscNameAsc(userId, false)
                : categories.findByUserIdAndKindAndArchivedOrderBySortOrderAscNameAsc(userId, kind, false);
        return list.stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse createCategory(UUID userId, CategoryRequest req) {
        Category category = categories.save(Category.builder()
                .userId(userId)
                .name(req.name().trim())
                .kind(req.kind() == null ? CategoryKind.EXPENSE : req.kind())
                .icon(orDefault(req.icon(), "tag"))
                .color(orDefault(req.color(), "#111111"))
                .parentId(req.parentId())
                .monthlyBudget(req.monthlyBudget())
                .sortOrder((int) categories.countByUserId(userId))
                .build());
        return CategoryResponse.from(category);
    }

    @Transactional
    public CategoryResponse updateCategory(UUID userId, UUID id, CategoryRequest req) {
        Category category = categories.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Category", id));
        if (req.name() != null && !req.name().isBlank()) {
            category.setName(req.name().trim());
        }
        if (req.kind() != null) {
            category.setKind(req.kind());
        }
        if (req.icon() != null) {
            category.setIcon(req.icon());
        }
        if (req.color() != null) {
            category.setColor(req.color());
        }
        if (req.monthlyBudget() != null) {
            category.setMonthlyBudget(req.monthlyBudget());
        }
        category.setParentId(req.parentId());
        return CategoryResponse.from(categories.save(category));
    }

    @Transactional
    public void deleteCategory(UUID userId, UUID id) {
        Category category = categories.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Category", id));
        if (category.isSystem()) {
            throw ApiException.badRequest("Built-in categories cannot be deleted — archive it instead");
        }
        category.setArchived(true);          // soft delete keeps old transactions labelled
        categories.save(category);
    }

    /** Gives a brand-new account holder something usable instead of an empty screen. */
    @Transactional
    public void seedDefaults(UUID userId, String currency) {
        if (accounts.countByUserId(userId) > 0 || categories.countByUserId(userId) > 0) {
            return;
        }
        accounts.save(Account.builder().userId(userId).name("Cash").type(AccountType.CASH)
                .currency(currency).icon("banknote").sortOrder(0).build());
        accounts.save(Account.builder().userId(userId).name("Bank").type(AccountType.BANK)
                .currency(currency).icon("landmark").sortOrder(1).build());

        record Seed(String name, String icon, CategoryKind kind) {
        }
        List<Seed> seeds = List.of(
                new Seed("Food & Drink", "utensils", CategoryKind.EXPENSE),
                new Seed("Groceries", "shopping-basket", CategoryKind.EXPENSE),
                new Seed("Transport", "bus", CategoryKind.EXPENSE),
                new Seed("Housing", "home", CategoryKind.EXPENSE),
                new Seed("Utilities", "plug", CategoryKind.EXPENSE),
                new Seed("Health", "heart-pulse", CategoryKind.EXPENSE),
                new Seed("Entertainment", "clapperboard", CategoryKind.EXPENSE),
                new Seed("Shopping", "shopping-bag", CategoryKind.EXPENSE),
                new Seed("Education", "graduation-cap", CategoryKind.EXPENSE),
                new Seed("Travel", "plane", CategoryKind.EXPENSE),
                new Seed("Subscriptions", "repeat", CategoryKind.EXPENSE),
                new Seed("Other", "circle-dashed", CategoryKind.EXPENSE),
                new Seed("Salary", "briefcase", CategoryKind.INCOME),
                new Seed("Bonus", "gift", CategoryKind.INCOME),
                new Seed("Freelance", "laptop", CategoryKind.INCOME),
                new Seed("Investments", "trending-up", CategoryKind.INCOME));

        for (int i = 0; i < seeds.size(); i++) {
            Seed s = seeds.get(i);
            categories.save(Category.builder()
                    .userId(userId).name(s.name()).icon(s.icon()).kind(s.kind())
                    .sortOrder(i).system(true).build());
        }
        log.info("Seeded default accounts and categories for user {}", userId);
    }

    // ========================================================== transactions
    @Transactional(readOnly = true)
    public Page<Transaction> search(UUID userId, LocalDate from, LocalDate to, UUID accountId,
                                    UUID categoryId, TxType type, BigDecimal min, BigDecimal max,
                                    String search, Pageable pageable) {
        return transactions.search(userId, from, to, accountId, categoryId, type, min, max,
                (search == null || search.isBlank()) ? null : search.trim(), pageable);
    }

    @Transactional
    public Transaction create(UUID userId, TransactionRequest req) {
        Account account = requireAccount(userId, req.accountId());
        validateTransfer(userId, req);

        Transaction tx = transactions.save(Transaction.builder()
                .userId(userId)
                .accountId(req.accountId())
                .toAccountId(req.type() == TxType.TRANSFER ? req.toAccountId() : null)
                .categoryId(req.type() == TxType.TRANSFER ? null : req.categoryId())
                .amount(req.amount().abs())
                .currency(req.currency() == null ? account.getCurrency() : req.currency().toUpperCase())
                .type(req.type())
                .occurredOn(req.occurredOn() == null ? LocalDate.now() : req.occurredOn())
                .note(req.note())
                .merchant(req.merchant())
                .tags(req.tags() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(req.tags()))
                .build());

        applyBalance(tx, +1);
        publish(Topics.Expense.TRANSACTION_ADDED, tx);
        return tx;
    }

    @Transactional
    public Transaction update(UUID userId, UUID id, TransactionRequest req) {
        Transaction tx = transactions.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Transaction", id));

        // Reverse the old effect before applying the new one — otherwise editing an
        // amount or moving a transaction between accounts silently corrupts balances.
        applyBalance(tx, -1);

        requireAccount(userId, req.accountId());
        validateTransfer(userId, req);

        tx.setAccountId(req.accountId());
        tx.setToAccountId(req.type() == TxType.TRANSFER ? req.toAccountId() : null);
        tx.setCategoryId(req.type() == TxType.TRANSFER ? null : req.categoryId());
        tx.setAmount(req.amount().abs());
        tx.setType(req.type());
        tx.setOccurredOn(req.occurredOn() == null ? tx.getOccurredOn() : req.occurredOn());
        tx.setNote(req.note());
        tx.setMerchant(req.merchant());
        tx.setTags(req.tags() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(req.tags()));
        if (req.currency() != null) {
            tx.setCurrency(req.currency().toUpperCase());
        }
        tx.setUpdatedAt(Instant.now());
        transactions.save(tx);

        applyBalance(tx, +1);
        publish(Topics.Expense.TRANSACTION_UPDATED, tx);
        return tx;
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Transaction tx = transactions.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Transaction", id));
        applyBalance(tx, -1);
        transactions.delete(tx);
        publish(Topics.Expense.TRANSACTION_DELETED, tx);
    }

    /** Maps a transaction onto its response shape, resolving account/category names. */
    @Transactional(readOnly = true)
    public List<TransactionResponse> decorate(UUID userId, List<Transaction> list) {
        Map<UUID, Account> accountsById = new HashMap<>();
        accounts.findByUserIdOrderBySortOrderAscNameAsc(userId).forEach(a -> accountsById.put(a.getId(), a));
        Map<UUID, Category> categoriesById = new HashMap<>();
        categories.findByUserIdAndArchivedOrderBySortOrderAscNameAsc(userId, false)
                .forEach(c -> categoriesById.put(c.getId(), c));
        categories.findByUserIdAndArchivedOrderBySortOrderAscNameAsc(userId, true)
                .forEach(c -> categoriesById.putIfAbsent(c.getId(), c));

        return list.stream().map(t -> {
            Account from = accountsById.get(t.getAccountId());
            Account to = t.getToAccountId() == null ? null : accountsById.get(t.getToAccountId());
            Category category = t.getCategoryId() == null ? null : categoriesById.get(t.getCategoryId());
            return new TransactionResponse(
                    t.getId(), t.getAccountId(), from == null ? null : from.getName(),
                    t.getToAccountId(), to == null ? null : to.getName(),
                    t.getCategoryId(), category == null ? null : category.getName(),
                    category == null ? null : category.getIcon(),
                    category == null ? null : category.getColor(),
                    t.getAmount(), t.signedAmount(), t.getCurrency(), t.getType(),
                    t.getOccurredOn(), t.getNote(), t.getMerchant(), t.getTags(),
                    t.getRecurringRuleId() != null, t.getCreatedAt());
        }).toList();
    }

    // =============================================================== helpers
    /** Applies (or with {@code sign = -1} reverses) a transaction's effect on balances. */
    private void applyBalance(Transaction tx, int sign) {
        BigDecimal amount = tx.getAmount().multiply(BigDecimal.valueOf(sign));

        accounts.findById(tx.getAccountId()).ifPresent(account -> {
            BigDecimal delta = switch (tx.getType()) {
                case INCOME -> amount;
                case EXPENSE, TRANSFER -> amount.negate();
            };
            account.setCurrentBalance(account.getCurrentBalance().add(delta));
            account.setUpdatedAt(Instant.now());
            accounts.save(account);
        });

        if (tx.getType() == TxType.TRANSFER && tx.getToAccountId() != null) {
            accounts.findById(tx.getToAccountId()).ifPresent(target -> {
                target.setCurrentBalance(target.getCurrentBalance().add(amount));
                target.setUpdatedAt(Instant.now());
                accounts.save(target);
            });
        }
    }

    private void validateTransfer(UUID userId, TransactionRequest req) {
        if (req.type() != TxType.TRANSFER) {
            return;
        }
        if (req.toAccountId() == null) {
            throw ApiException.badRequest("A transfer needs a destination account");
        }
        if (req.toAccountId().equals(req.accountId())) {
            throw ApiException.badRequest("A transfer must move money between two different accounts");
        }
        requireAccount(userId, req.toAccountId());
    }

    private void publish(String type, Transaction tx) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transactionId", tx.getId().toString());
        payload.put("accountId", tx.getAccountId().toString());
        payload.put("categoryId", tx.getCategoryId() == null ? null : tx.getCategoryId().toString());
        payload.put("amount", tx.getAmount());
        payload.put("currency", tx.getCurrency());
        payload.put("txType", tx.getType().name());
        payload.put("occurredOn", tx.getOccurredOn().toString());
        payload.put("merchant", tx.getMerchant());
        events.publish(Topics.EXPENSE_EVENTS, DomainEvent.of(type, "Transaction",
                tx.getId().toString(), tx.getUserId(), 0L, payload));
    }

    private Account requireAccount(UUID userId, UUID id) {
        return accounts.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Account", id));
    }

    /** Whatever the caller sent, rows are stored in the one system currency. */
    private static String currencyOr(String value) {
        return Money.normalise(value);
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
