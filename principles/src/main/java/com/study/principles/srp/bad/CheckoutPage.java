package com.study.principles.srp.bad;

import com.study.principles.srp.Browser;
import com.study.principles.srp.Item;
import com.study.principles.srp.ShopApi;
import com.study.principles.srp.ShopDb;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * "Everything about checkout" in one page object. Five reasons to change, five different owners:
 * <pre>
 *   markup (front-end)    selectors, loaded-check in the constructor, payWith()
 *   setup channel (API)   prepareCart()
 *   pricing (product)     expectedTotal() — a copy of the business rule, private, reachable only via the UI
 *   persistence (DB)      assertOrderInDb()
 *   reporting (infra)     report() in every method, hard-wired to System.out, card number in clear
 *   test data (QA)        randomCard()
 * </pre>
 * The constructor needs a browser, an API client and a DB, and checks the page is loaded: so even a test
 * that only uses the API/DB helpers breaks when the front-end renames a selector.
 */
public class CheckoutPage {

    private final Browser browser;
    private final ShopApi api;
    private final ShopDb db;

    public CheckoutPage(Browser browser, ShopApi api, ShopDb db) {
        this.browser = browser;
        this.api = api;
        this.db = db;
        report("open checkout");
        browser.open("/checkout");
        if (!browser.isVisible("#checkout-form")) {
            throw new IllegalStateException("checkout page is not loaded: #checkout-form not visible");
        }
    }

    public CheckoutPage prepareCart(String user, List<Item> items) {
        report("add " + items.size() + " items to the cart of " + user + " via API");
        items.forEach(item -> api.addToCart(user, item));
        return this;
    }

    public String randomCard() {
        StringBuilder card = new StringBuilder("4");
        for (int i = 1; i < 16; i++) card.append(ThreadLocalRandom.current().nextInt(10));
        return card.toString();
    }

    public String payWith(String card) {
        report("pay with card " + card);
        browser.type("#card", card);
        browser.click("#pay");
        return browser.text("#order-id");
    }

    public void verifyTotal(List<Item> items) {
        report("verify total");
        BigDecimal actual = new BigDecimal(browser.text("#total"));
        BigDecimal expected = expectedTotal(items);
        if (actual.compareTo(expected) != 0) {
            throw new AssertionError("total: expected " + expected + " but was " + actual);
        }
    }

    public void assertOrderInDb(String orderId, List<Item> items) {
        report("verify order " + orderId + " in DB");
        BigDecimal stored = db.findOrder(orderId)
                .orElseThrow(() -> new AssertionError("no order " + orderId))
                .total();
        if (stored.compareTo(expectedTotal(items)) != 0) {
            throw new AssertionError("stored total: expected " + expectedTotal(items) + " but was " + stored);
        }
    }

    private BigDecimal expectedTotal(List<Item> items) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Item item : items) subtotal = subtotal.add(item.price().multiply(BigDecimal.valueOf(item.quantity())));
        if (subtotal.compareTo(new BigDecimal("100")) >= 0) subtotal = subtotal.multiply(new BigDecimal("0.9"));
        return subtotal.multiply(new BigDecimal("1.2")).setScale(2, RoundingMode.HALF_UP);
    }

    private void report(String step) {
        System.out.println("[STEP] " + step);
    }
}
