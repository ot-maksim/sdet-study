package com.study.principles.kiss.flags.bad;

import com.study.principles.kiss.flags.FakeShop;
import com.study.principles.kiss.flags.Order;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One step for every order a test may need. It started as {@code placeOrder(customer)}: create, pay, check PAID.
 * Then each new test added a flag — guest checkout, promo, "don't pay, I test the pending page", saved card,
 * "skip the check, I assert myself". Each flag was a one-line {@code if}; nobody wrote the 2^5 cases down.
 *
 * <p>Call sites read like {@code placeOrder("alice", true, false, true, false, true)}.
 */
public final class OrderSteps {

    private static final String TEST_CARD = "4111111111111111";

    private final FakeShop shop;

    public OrderSteps(FakeShop shop) {
        this.shop = shop;
    }

    public Order placeOrder(String customer, boolean guest, boolean promo, boolean pay, boolean savedCard,
                            boolean verify) {
        long id = shop.createOrder(guest ? null : customer);
        if (promo) {
            shop.applyPromo(id, FakeShop.PROMO);
        }
        if (pay) {
            if (savedCard) {
                shop.payWithSavedCard(id);
            } else {
                shop.payWithCard(id, TEST_CARD);
            }
        }
        // "verify" predates "promo" and "pay": it checked PAID, then got wrapped when unpaid orders appeared
        if (verify && pay) {
            assertThat(shop.order(id).status()).isEqualTo(Order.Status.PAID);
        }
        return shop.order(id);
    }
}
