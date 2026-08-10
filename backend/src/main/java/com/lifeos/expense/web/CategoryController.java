package com.lifeos.expense.web;

import com.lifeos.common.security.UserPrincipal;
import com.lifeos.expense.domain.ExpenseEnums.CategoryKind;
import com.lifeos.expense.dto.ExpenseDtos.CategoryRequest;
import com.lifeos.expense.dto.ExpenseDtos.CategoryResponse;
import com.lifeos.expense.service.LedgerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "Categories")
public class CategoryController {

    private final LedgerService ledger;

    public CategoryController(LedgerService ledger) {
        this.ledger = ledger;
    }

    @GetMapping
    public List<CategoryResponse> list(@AuthenticationPrincipal UserPrincipal me,
                                       @RequestParam(required = false) CategoryKind kind) {
        return ledger.listCategories(me.id(), kind);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@AuthenticationPrincipal UserPrincipal me,
                                   @Valid @RequestBody CategoryRequest req) {
        return ledger.createCategory(me.id(), req);
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@AuthenticationPrincipal UserPrincipal me,
                                   @PathVariable UUID id,
                                   @RequestBody CategoryRequest req) {
        return ledger.updateCategory(me.id(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal me, @PathVariable UUID id) {
        ledger.deleteCategory(me.id(), id);
    }
}
