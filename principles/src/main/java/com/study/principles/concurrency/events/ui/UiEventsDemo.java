package com.study.principles.concurrency.events.ui;

import com.study.principles.concurrency.events.ui.good.CheckoutPage;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Two checks on the checkout page, 20 runs each way. The API answers in 0–150 ms (sometimes from a cache);
 * a screenshot takes 100 ms; the toast lives 300 ms.
 * <ol>
 *   <li>"Paying returns the payment response."</li>
 *   <li>"After paying, the toast says Payment successful", with other checks (300 ms) done first.</li>
 * </ol>
 *
 * <pre>
 * mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.concurrency.events.ui.UiEventsDemo
 * </pre>
 */
public final class UiEventsDemo {

    private static final int RUNS = 20;
    private static final Duration TOAST = Duration.ofMillis(300);

    public static void main(String[] args) {
        run("bad: click, screenshot, waitForResponse", () -> {
            try (FakePage page = open()) {
                new com.study.principles.concurrency.events.ui.bad.CheckoutPage(page).submitPayment();
                return true;
            } catch (TimeoutError e) {
                return false;
            }
        });
        run("good: waitForResponse(predicate, click)", () -> {
            try (FakePage page = open()) {
                new CheckoutPage(page).pay();
                return true;
            } catch (TimeoutError e) {
                return false;
            }
        });
        run("bad: other checks, then expect the toast", () -> {
            try (FakePage page = open()) {
                return toastAfterOtherChecks(page);
            }
        });
        run("good: clock installed, same order", () -> {
            try (FakePage page = open()) {
                page.clock().install();
                return toastAfterOtherChecks(page);
            }
        });
    }

    private static boolean toastAfterOtherChecks(FakePage page) {
        CheckoutPage checkout = new CheckoutPage(page);
        checkout.pay();
        checkout.status().hasText("PAID");
        page.screenshot();
        page.screenshot();
        page.screenshot();
        try {
            checkout.toast().withTimeout(Duration.ofMillis(500)).hasText("Payment successful");
            return true;
        } catch (AssertionError e) {
            return false;
        }
    }

    private static FakePage open() {
        FakePage page = CheckoutApp.open(Duration.ofMillis(ThreadLocalRandom.current().nextLong(0, 150)), TOAST);
        page.setDefaultTimeout(Duration.ofMillis(500));
        return page;
    }

    private interface Check {
        boolean passed();
    }

    private static void run(String name, Check check) {
        int ok = 0;
        for (int i = 0; i < RUNS; i++) {
            ok += check.passed() ? 1 : 0;
        }
        System.out.printf("%-44s %2d/%d ok%n", name, ok, RUNS);
    }
}
