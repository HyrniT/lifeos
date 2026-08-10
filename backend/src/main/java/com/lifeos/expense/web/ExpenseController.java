package com.lifeos.expense.web;

import com.lifeos.common.api.PageResponse;
import com.lifeos.common.security.UserPrincipal;
import com.lifeos.expense.domain.ExpenseEnums.TxType;
import com.lifeos.expense.domain.Transaction;
import com.lifeos.expense.dto.ExpenseDtos.*;
import com.lifeos.expense.service.LedgerService;
import com.lifeos.expense.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/expenses")
@Tag(name = "Transactions & money statistics")
public class ExpenseController {

    private final LedgerService ledger;
    private final StatisticsService statistics;

    public ExpenseController(LedgerService ledger, StatisticsService statistics) {
        this.ledger = ledger;
        this.statistics = statistics;
    }

    @GetMapping
    @Operation(summary = "Search transactions with any combination of filters")
    public PageResponse<TransactionResponse> list(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) TxType type,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusMonths(1).withDayOfMonth(1) : from;

        Page<Transaction> result = ledger.search(me.id(), start, end, accountId, categoryId, type,
                minAmount, maxAmount, search, PageRequest.of(page, Math.min(size, 200)));

        List<TransactionResponse> decorated = ledger.decorate(me.id(), result.getContent());
        return new PageResponse<>(decorated, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse create(@AuthenticationPrincipal UserPrincipal me,
                                      @Valid @RequestBody TransactionRequest req) {
        return ledger.decorate(me.id(), List.of(ledger.create(me.id(), req))).get(0);
    }

    @PutMapping("/{id}")
    public TransactionResponse update(@AuthenticationPrincipal UserPrincipal me,
                                      @PathVariable UUID id,
                                      @Valid @RequestBody TransactionRequest req) {
        return ledger.decorate(me.id(), List.of(ledger.update(me.id(), id, req))).get(0);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id) {
        ledger.delete(me.id(), id);
    }

    // ---------------------------------------------------------- statistics
    @GetMapping("/statistics")
    @Operation(summary = "Everything the money dashboard needs in one call")
    public ExpenseStatistics statistics(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        return statistics.compute(me.id(), start, end);
    }

    @GetMapping("/statistics/overview")
    public MoneyOverview overview(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        return statistics.overview(me.id(), start, end);
    }

    @GetMapping("/statistics/trend")
    public List<TrendPoint> trend(@AuthenticationPrincipal UserPrincipal me,
                                  @RequestParam(defaultValue = "12") int months) {
        return statistics.monthlyTrend(me.id(), Math.min(36, Math.max(1, months)));
    }
}
