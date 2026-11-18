package com.study.principles.srp.overapplied;

import com.study.principles.srp.Browser;

public final class CheckoutActions {

    private final Browser browser;

    public CheckoutActions(Browser browser) {
        this.browser = browser;
    }

    public void open() {
        browser.open("/checkout");
    }

    public void enterCard(String card) {
        browser.type(CheckoutLocators.CARD, card);
    }

    public void clickPay() {
        browser.click(CheckoutLocators.PAY);
    }
}
