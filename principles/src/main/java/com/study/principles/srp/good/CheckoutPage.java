package com.study.principles.srp.good;

import com.study.principles.srp.Browser;

import java.math.BigDecimal;

/**
 * One reason to change: the checkout <b>markup</b>. Selectors, the loaded-check and the actions change
 * together when the front-end changes, so they stay together. No API, no DB, no expected values, no
 * reporting: the page returns what the screen shows and the test decides whether it is right.
 */
public final class CheckoutPage {

    private static final String FORM = "#checkout-form";
    private static final String CARD = "#card";
    private static final String PAY = "#pay";
    private static final String TOTAL = "#total";
    private static final String ORDER_ID = "#order-id";

    private final Browser browser;

    private CheckoutPage(Browser browser) {
        this.browser = browser;
    }

    public static CheckoutPage open(Browser browser) {
        browser.open("/checkout");
        if (!browser.isVisible(FORM)) throw new IllegalStateException("checkout page is not loaded: " + FORM);
        return new CheckoutPage(browser);
    }

    public BigDecimal total() {
        return new BigDecimal(browser.text(TOTAL));
    }

    /** @return the order id shown after payment */
    public String payWith(String card) {
        browser.type(CARD, card);
        browser.click(PAY);
        return browser.text(ORDER_ID);
    }
}
