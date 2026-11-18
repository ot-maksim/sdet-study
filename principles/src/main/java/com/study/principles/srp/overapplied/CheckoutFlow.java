package com.study.principles.srp.overapplied;

import com.study.principles.srp.Browser;

/**
 * And because nobody wants to wire four objects in every test, a fifth class glues them back together —
 * the original page object, now spread over five files.
 */
public final class CheckoutFlow {

    private final CheckoutActions actions;
    private final CheckoutWaits waits;
    private final CheckoutVerifications verifications;

    public CheckoutFlow(Browser browser) {
        this.actions = new CheckoutActions(browser);
        this.waits = new CheckoutWaits(browser);
        this.verifications = new CheckoutVerifications(browser);
    }

    public CheckoutFlow open() {
        actions.open();
        waits.untilLoaded();
        return this;
    }

    public String payWith(String card) {
        actions.enterCard(card);
        actions.clickPay();
        waits.untilPaid();
        return verifications.orderId();
    }

    public CheckoutVerifications verify() {
        return verifications;
    }
}
