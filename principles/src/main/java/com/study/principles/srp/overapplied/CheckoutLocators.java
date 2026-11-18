package com.study.principles.srp.overapplied;

/**
 * SRP over-applied: the page is cut by <i>technical role</i> — locators, waits, actions, verifications —
 * not by reason to change. All four change for the same reason (the markup), so one UI change touches all
 * four: adding a promo-code field = a locator here, a wait, an action and a read, in four files.
 * <p>
 * Cohesion test: "if X changes, what else changes with it?" Things that always change together belong
 * in one class.
 */
public final class CheckoutLocators {

    public static final String FORM = "#checkout-form";
    public static final String CARD = "#card";
    public static final String PAY = "#pay";
    public static final String TOTAL = "#total";
    public static final String ORDER_ID = "#order-id";

    private CheckoutLocators() {
    }
}
