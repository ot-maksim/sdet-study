package com.study.principles.kiss.yagni.good.v1;

import java.io.PrintStream;

/**
 * Version 1, while the console was the only need: no interface, no factory, no config. The step structure is
 * in the code ({@code step} around a body), because the steps <em>are</em> nested — that is a fact of today,
 * not a guess about tomorrow's reporters.
 */
public final class CheckoutSteps {

    private final PrintStream out;
    private int depth;

    public CheckoutSteps(PrintStream out) {
        this.out = out;
    }

    public void buy() {
        step("buy", () -> {
            step("add to cart", () -> { });
            step("pay", () -> step("confirm 3DS", () -> { }));
        });
    }

    private void step(String name, Runnable body) {
        out.println("  ".repeat(depth) + "[STEP] " + name);
        depth++;
        try {
            body.run();
        } finally {
            depth--;
        }
    }
}
