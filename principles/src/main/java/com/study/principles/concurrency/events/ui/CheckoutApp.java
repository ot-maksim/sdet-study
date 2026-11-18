package com.study.principles.concurrency.events.ui;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The SUT's front end: a checkout page. Clicking {@code #pay}:
 * <ol>
 *   <li>shows {@code confirm("Pay 42.00 EUR?")}; dismissed → {@code #status} = {@code CANCELLED}, nothing is sent;</li>
 *   <li>accepted → {@code #status} = {@code PROCESSING}, {@code POST /api/payments};</li>
 *   <li>after {@code apiLatency} the response arrives: {@code #status} = {@code PAID}, a {@code #toast}
 *       "Payment successful" appears and hides itself after {@code toastDuration} ({@code setTimeout}).</li>
 * </ol>
 */
public final class CheckoutApp {

    public static final String PAY = "#pay";
    public static final String STATUS = "#status";
    public static final String TOAST = "#toast";
    public static final String PAYMENTS_URL = "/api/payments";
    public static final String DIALOG = "Pay 42.00 EUR?";

    private CheckoutApp() {
    }

    /** A page that takes 100 ms per screenshot. */
    public static FakePage open(Duration apiLatency, Duration toastDuration) {
        FakePage page = new FakePage(Duration.ofMillis(100));
        AtomicLong payments = new AtomicLong();
        page.setText(STATUS, "NEW");
        page.onClick(PAY, () -> {
            if (!page.dialog(DIALOG)) {
                page.setText(STATUS, "CANCELLED");
                return;
            }
            page.setText(STATUS, "PROCESSING");
            String paymentId = "pay_" + payments.incrementAndGet();
            Response created = new Response("POST", PAYMENTS_URL, 201,
                    "{\"paymentId\":\"" + paymentId + "\",\"status\":\"PAID\"}");
            page.fetch(created, apiLatency, response -> {
                page.setText(STATUS, "PAID");
                page.setText(TOAST, "Payment successful");
                page.setTimeout(() -> page.remove(TOAST), toastDuration);
            });
        });
        return page;
    }
}
