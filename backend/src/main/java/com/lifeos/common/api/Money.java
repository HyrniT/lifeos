package com.lifeos.common.api;

/**
 * The single currency this system stores money in.
 *
 * LifeOS is single-currency by design. Every amount is in {@value #BASE_CURRENCY},
 * so any two figures are directly comparable and no row needs an exchange rate to
 * be understood. Historic transactions keep the value they were recorded with,
 * which is the record an expense tracker exists to preserve.
 *
 * The currency columns on the entities are kept rather than dropped: they cost a
 * few bytes, they state plainly what each row holds, and removing them would be a
 * destructive migration for the sake of tidiness. Writers set them from here.
 */
public final class Money {

    public static final String BASE_CURRENCY = "VND";

    private Money() {
    }

    /**
     * Normalises any client-supplied currency to the one this system keeps.
     *
     * Requests naming another currency are normalised rather than rejected:
     * the amount is in {@value #BASE_CURRENCY} either way, so failing the write
     * would only break older clients that still send a code.
     */
    public static String normalise(String ignored) {
        return BASE_CURRENCY;
    }
}
