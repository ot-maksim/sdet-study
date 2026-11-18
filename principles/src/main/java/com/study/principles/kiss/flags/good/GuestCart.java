package com.study.principles.kiss.flags.good;

import com.study.principles.kiss.flags.FakeShop;

/** A guest's cart: it can pay with a card or stay unpaid. There is no saved card to pay with — no such method. */
public final class GuestCart {

    private final Draft draft;

    GuestCart(FakeShop shop, CreatedOrders created) {
        this.draft = new Draft(shop, created, null);
    }

    public GuestCart withPromo(String code) {
        draft.promo(code);
        return this;
    }

    public PaidOrder payWith(String cardNumber) {
        return draft.payWith(cardNumber);
    }

    public PendingOrder leaveUnpaid() {
        return draft.leaveUnpaid();
    }
}
