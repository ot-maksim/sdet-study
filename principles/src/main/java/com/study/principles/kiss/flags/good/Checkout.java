package com.study.principles.kiss.flags.good;

import com.study.principles.kiss.flags.FakeShop;

/**
 * Entry point of the order steps. Who is buying is a choice of method, and the choice decides which further
 * methods exist: {@code checkout.asGuest().payWithSavedCard()} does not compile.
 *
 * <pre>
 * PaidOrder order = checkout.as(alice).withPromo("SAVE5").payWithSavedCard();
 * PendingOrder pending = checkout.asGuest().leaveUnpaid();
 * </pre>
 *
 * There is no {@code verify}: the test asserts what it cares about on the returned order.
 */
public final class Checkout {

    private final FakeShop shop;
    private final CreatedOrders created;

    public Checkout(FakeShop shop, CreatedOrders created) {
        this.shop = shop;
        this.created = created;
    }

    public GuestCart asGuest() {
        return new GuestCart(shop, created);
    }

    public CustomerCart as(String customer) {
        return new CustomerCart(shop, created, customer);
    }
}
