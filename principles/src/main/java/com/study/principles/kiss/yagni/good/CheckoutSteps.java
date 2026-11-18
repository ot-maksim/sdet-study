package com.study.principles.kiss.yagni.good;

/** Version 2 of the steps: the same {@code buy()}, reporting to whichever listener the root wires in. */
public final class CheckoutSteps {

    private final Steps steps;

    public CheckoutSteps(Steps steps) {
        this.steps = steps;
    }

    public void buy() {
        steps.step("buy", () -> {
            steps.step("add to cart");
            steps.step("pay", () -> steps.step("confirm 3DS"));
        });
    }
}
