package com.study.principles.srp;

/**
 * Two versions of the checkout markup: V2 is the front-end team's redesign ({@code data-test} attributes).
 * Behaviour, prices and the API are the same — only the reason "markup" changed.
 */
public enum Markup {
    V1("#checkout-form", "#card", "#pay", "#total", "#order-id"),
    V2("[data-test=checkout]", "[data-test=card]", "[data-test=pay]", "[data-test=total]", "[data-test=order-id]");

    public final String form;
    public final String card;
    public final String pay;
    public final String total;
    public final String orderId;

    Markup(String form, String card, String pay, String total, String orderId) {
        this.form = form;
        this.card = card;
        this.pay = pay;
        this.total = total;
        this.orderId = orderId;
    }
}
