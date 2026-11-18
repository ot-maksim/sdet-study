package com.study.principles.kiss.flags.good;

import com.study.principles.kiss.flags.FakeShop;

/** A registered customer's cart: everything a guest can do, plus the saved card. */
public final class CustomerCart {

    private final Draft draft;

    CustomerCart(FakeShop shop, CreatedOrders created, String customer) {
        this.draft = new Draft(shop, created, customer);
    }

    public CustomerCart withPromo(String code) {
        draft.promo(code);
        return this;
    }

    public PaidOrder payWith(String cardNumber) {
        return draft.payWith(cardNumber);
    }

    public PaidOrder payWithSavedCard() {
        return draft.payWithSavedCard();
    }

    public PendingOrder leaveUnpaid() {
        return draft.leaveUnpaid();
    }
}
