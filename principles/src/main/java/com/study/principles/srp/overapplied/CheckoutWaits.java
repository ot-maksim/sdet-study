package com.study.principles.srp.overapplied;

import com.study.principles.srp.Browser;

public final class CheckoutWaits {

    private final Browser browser;

    public CheckoutWaits(Browser browser) {
        this.browser = browser;
    }

    public void untilLoaded() {
        if (!browser.isVisible(CheckoutLocators.FORM)) {
            throw new IllegalStateException("checkout page is not loaded: " + CheckoutLocators.FORM);
        }
    }

    public void untilPaid() {
        if (!browser.isVisible(CheckoutLocators.ORDER_ID)) throw new IllegalStateException("no order id shown");
    }
}
