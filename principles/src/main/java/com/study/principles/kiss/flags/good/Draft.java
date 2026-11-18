package com.study.principles.kiss.flags.good;

import com.study.principles.kiss.flags.FakeShop;

/**
 * The shared mechanics of both carts. Package-private: callers choose an intention on a cart, they never see
 * the parameters the intentions differ in.
 */
final class Draft {

    private final FakeShop shop;
    private final CreatedOrders created;
    private final String customer;
    private String promo;

    Draft(FakeShop shop, CreatedOrders created, String customer) {
        this.shop = shop;
        this.created = created;
        this.customer = customer;
    }

    void promo(String code) {
        this.promo = code;
    }

    PaidOrder payWith(String cardNumber) {
        long id = create();
        shop.payWithCard(id, cardNumber);
        return new PaidOrder(shop.order(id));
    }

    PaidOrder payWithSavedCard() {
        long id = create();
        shop.payWithSavedCard(id);
        return new PaidOrder(shop.order(id));
    }

    PendingOrder leaveUnpaid() {
        return new PendingOrder(shop.order(create()));
    }

    private long create() {
        long id = created.register(shop.createOrder(customer));
        if (promo != null) {
            shop.applyPromo(id, promo);
        }
        return id;
    }
}
