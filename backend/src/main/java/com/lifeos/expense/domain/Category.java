package com.lifeos.expense.domain;

import com.lifeos.expense.domain.ExpenseEnums.CategoryKind;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "category", schema = "expense", indexes = {
        @Index(name = "idx_category_user", columnList = "user_id,kind,sort_order")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private CategoryKind kind = CategoryKind.EXPENSE;

    @Column(length = 48)
    @Builder.Default
    private String icon = "tag";

    @Column(length = 16)
    @Builder.Default
    private String color = "#111111";

    /** Nullable: a category with no parent is a top-level group. */
    @Column(name = "parent_id")
    private UUID parentId;

    /** Convenience budget on the category itself; the budget table can override it. */
    @Column(name = "monthly_budget", precision = 19, scale = 4)
    private BigDecimal monthlyBudget;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean archived = false;

    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private boolean system = false;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
