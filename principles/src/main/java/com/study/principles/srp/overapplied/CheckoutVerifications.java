package com.study.principles.srp.overapplied;

import com.study.principles.srp.Browser;

import java.math.BigDecimal;

public final class CheckoutVerifications {

    private final Browser browser;

    public CheckoutVerifications(Browser browser) {
        this.browser = browser;
    }

    public BigDecimal total() {
        return new BigDecimal(browser.text(CheckoutLocators.TOTAL));
    }

    public String orderId() {
        return browser.text(CheckoutLocators.ORDER_ID);
    }
}
