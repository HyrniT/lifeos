package com.lifeos.expense.service;

import com.lifeos.common.exception.ApiException;
import com.lifeos.expense.domain.ExpenseEnums.Cadence;
import com.lifeos.expense.domain.RecurringRule;
import com.lifeos.expense.dto.ExpenseDtos.RecurringRequest;
import com.lifeos.expense.dto.ExpenseDtos.TransactionRequest;
import com.lifeos.expense.repo.RecurringRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Materialises recurring transactions when they come due.
 *
 * The catch-up loop matters: if the service was down for a week, every missed
 * occurrence still gets created rather than silently skipped.
 */
@Service
public class RecurringService {

    private static final Logger log = LoggerFactory.getLogger(RecurringService.class);
    private static final int MAX_CATCH_UP_PER_RULE = 60;

    private final RecurringRuleRepository rules;
    private final LedgerService ledger;

    public RecurringService(RecurringRuleRepository rules, LedgerService ledger) {
        this.rules = rules;
        this.ledger = ledger;
    }

    @Transactional(readOnly = true)
    public List<RecurringRule> list(UUID userId) {
        return rules.findByUserIdOrderByNextRunOnAsc(userId);
    }

    @Transactional
    public RecurringRule create(UUID userId, RecurringRequest req) {
        return rules.save(RecurringRule.builder()
                .userId(userId)
                .name(req.name().trim())
                .accountId(req.accountId())
                .categoryId(req.categoryId())
                .amount(req.amount().abs())
                .currency(req.currency() == null ? "USD" : req.currency().toUpperCase())
                .type(req.type())
                .cadence(req.cadence() == null ? Cadence.MONTHLY : req.cadence())
                .nextRunOn(req.nextRunOn())
                .endOn(req.endOn())
                .note(req.note())
                .build());
    }

    @Transactional
    public RecurringRule update(UUID userId, UUID id, RecurringRequest req) {
        RecurringRule rule = rules.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Recurring rule", id));
        rule.setName(req.name().trim());
        rule.setAccountId(req.accountId());
        rule.setCategoryId(req.categoryId());
        rule.setAmount(req.amount().abs());
        rule.setType(req.type());
        if (req.cadence() != null) {
            rule.setCadence(req.cadence());
        }
        rule.setNextRunOn(req.nextRunOn());
        rule.setEndOn(req.endOn());
        rule.setNote(req.note());
        return rules.save(rule);
    }

    @Transactional
    public void setActive(UUID userId, UUID id, boolean active) {
        RecurringRule rule = rules.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Recurring rule", id));
        rule.setActive(active);
        rules.save(rule);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        RecurringRule rule = rules.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Recurring rule", id));
        rules.delete(rule);
    }

    /** Runs hourly so a restart never leaves a rule unprocessed for a whole day. */
    @Scheduled(cron = "${lifeos.recurring.cron:0 5 * * * *}")
    @Transactional
    public void materialiseDue() {
        LocalDate today = LocalDate.now();
        List<RecurringRule> due = rules.findByActiveTrueAndNextRunOnLessThanEqual(today);
        if (due.isEmpty()) {
            return;
        }

        int created = 0;
        for (RecurringRule rule : due) {
            int guard = 0;
            while (rule.isActive()
                    && !rule.getNextRunOn().isAfter(today)
                    && guard++ < MAX_CATCH_UP_PER_RULE) {

                if (rule.getEndOn() != null && rule.getNextRunOn().isAfter(rule.getEndOn())) {
                    rule.setActive(false);
                    break;
                }

                try {
                    var tx = ledger.create(rule.getUserId(), new TransactionRequest(
                            rule.getAccountId(), null, rule.getCategoryId(), rule.getAmount(),
                            rule.getCurrency(), rule.getType(), rule.getNextRunOn(),
                            rule.getNote() == null ? rule.getName() : rule.getNote(),
                            rule.getName(), Set.of("recurring")));
                    tx.setRecurringRuleId(rule.getId());
                    created++;
                } catch (Exception ex) {
                    // A deleted account should disable the rule, not stall the scheduler.
                    log.warn("Recurring rule {} failed and was deactivated: {}", rule.getId(), ex.getMessage());
                    rule.setActive(false);
                    break;
                }

                rule.setLastRunOn(rule.getNextRunOn());
                rule.setNextRunOn(rule.advance(rule.getNextRunOn()));
            }
            rules.save(rule);
        }
        log.info("Recurring scheduler created {} transaction(s) from {} rule(s)", created, due.size());
    }
}
