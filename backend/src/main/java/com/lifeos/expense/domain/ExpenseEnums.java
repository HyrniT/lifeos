package com.lifeos.expense.domain;

public final class ExpenseEnums {

    private ExpenseEnums() {
    }

    public enum AccountType {
        CASH, BANK, CREDIT_CARD, E_WALLET, SAVINGS, INVESTMENT, LOAN
    }

    public enum TxType {
        EXPENSE, INCOME, TRANSFER
    }

    public enum CategoryKind {
        EXPENSE, INCOME
    }

    public enum BudgetPeriod {
        WEEKLY, MONTHLY, QUARTERLY, YEARLY
    }

    public enum Cadence {
        DAILY, WEEKLY, BIWEEKLY, MONTHLY, QUARTERLY, YEARLY
    }
}
