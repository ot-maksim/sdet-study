package com.study.principles.kiss.yagni.bad;

/** Steps that report through the guessed interface: each step is a line, so nesting has nowhere to go. */
public final class CheckoutSteps {

    private final Reporter reporter;

    public CheckoutSteps(Reporter reporter) {
        this.reporter = reporter;
    }

    public void buy() {
        reporter.report("buy");
        reporter.report("add to cart");
        pay();
    }

    private void pay() {
        reporter.report("pay");
        reporter.report("confirm 3DS");
    }
}
