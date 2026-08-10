package com.lifeos.expense.web;

import com.lifeos.common.security.UserPrincipal;
import com.lifeos.expense.dto.ExpenseDtos.AccountRequest;
import com.lifeos.expense.dto.ExpenseDtos.AccountResponse;
import com.lifeos.expense.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Accounts")
public class AccountController {

    private final LedgerService ledger;

    public AccountController(LedgerService ledger) {
        this.ledger = ledger;
    }

    @GetMapping
    public List<AccountResponse> list(@AuthenticationPrincipal UserPrincipal me,
                                      @RequestParam(defaultValue = "false") boolean includeArchived) {
        return ledger.listAccounts(me.id(), includeArchived);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@AuthenticationPrincipal UserPrincipal me,
                                  @Valid @RequestBody AccountRequest req) {
        return ledger.createAccount(me.id(), req);
    }

    @PutMapping("/{id}")
    public AccountResponse update(@AuthenticationPrincipal UserPrincipal me,
                                  @PathVariable UUID id,
                                  @RequestBody AccountRequest req) {
        return ledger.updateAccount(me.id(), id, req);
    }

    @PostMapping("/{id}/archive")
    public Map<String, Object> archive(@AuthenticationPrincipal UserPrincipal me,
                                       @PathVariable UUID id,
                                       @RequestParam(defaultValue = "true") boolean archived) {
        ledger.archiveAccount(me.id(), id, archived);
        return Map.of("id", id, "archived", archived);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id) {
        ledger.deleteAccount(me.id(), id);
    }

    @PostMapping("/seed-defaults")
    @Operation(summary = "Create the starter accounts and categories for a new user")
    public List<AccountResponse> seed(@AuthenticationPrincipal UserPrincipal me,
                                      @RequestParam(defaultValue = "USD") String currency) {
        ledger.seedDefaults(me.id(), currency.toUpperCase());
        return ledger.listAccounts(me.id(), false);
    }
}
