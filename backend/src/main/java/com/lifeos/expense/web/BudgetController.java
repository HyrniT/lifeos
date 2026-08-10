package com.lifeos.expense.web;

import com.lifeos.common.security.UserPrincipal;
import com.lifeos.expense.domain.Budget;
import com.lifeos.expense.domain.RecurringRule;
import com.lifeos.expense.dto.ExpenseDtos.*;
import com.lifeos.expense.service.BudgetService;
import com.lifeos.expense.service.RecurringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/budgets")
@Tag(name = "Budgets & recurring rules")
public class BudgetController {

    private final BudgetService budgets;
    private final RecurringService recurring;

    public BudgetController(BudgetService budgets, RecurringService recurring) {
        this.budgets = budgets;
        this.recurring = recurring;
    }

    @GetMapping
    @Operation(summary = "Budgets with live spend, pace and safe daily allowance")
    public List<BudgetStatus> list(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on) {
        return budgets.statuses(me.id(), on);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Budget create(@AuthenticationPrincipal UserPrincipal me, @Valid @RequestBody BudgetRequest req) {
        return budgets.create(me.id(), req);
    }

    @PutMapping("/{id}")
    public Budget update(@AuthenticationPrincipal UserPrincipal me,
                         @PathVariable UUID id, @RequestBody BudgetRequest req) {
        return budgets.update(me.id(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id) {
        budgets.delete(me.id(), id);
    }

    // ------------------------------------------------------------ recurring
    @GetMapping("/recurring")
    public List<RecurringResponse> listRecurring(@AuthenticationPrincipal UserPrincipal me) {
        return recurring.list(me.id()).stream().map(RecurringResponse::from).toList();
    }

    @PostMapping("/recurring")
    @ResponseStatus(HttpStatus.CREATED)
    public RecurringResponse createRecurring(@AuthenticationPrincipal UserPrincipal me,
                                             @Valid @RequestBody RecurringRequest req) {
        return RecurringResponse.from(recurring.create(me.id(), req));
    }

    @PutMapping("/recurring/{id}")
    public RecurringResponse updateRecurring(@AuthenticationPrincipal UserPrincipal me,
                                             @PathVariable UUID id,
                                             @Valid @RequestBody RecurringRequest req) {
        return RecurringResponse.from(recurring.update(me.id(), id, req));
    }

    @PostMapping("/recurring/{id}/toggle")
    public Map<String, Object> toggleRecurring(@AuthenticationPrincipal UserPrincipal me,
                                               @PathVariable UUID id,
                                               @RequestParam boolean active) {
        recurring.setActive(me.id(), id, active);
        return Map.of("id", id, "active", active);
    }

    @DeleteMapping("/recurring/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRecurring(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id) {
        recurring.delete(me.id(), id);
    }
}
