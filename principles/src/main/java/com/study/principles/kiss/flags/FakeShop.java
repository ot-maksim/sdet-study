package com.study.principles.kiss.flags;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The shop under test: orders for guests and customers, a promo code, payment by a new or a saved card.
 * {@link Build} deploys regressions, to see which checks notice them.
 */
public final class FakeShop {

    public static final int CART_TOTAL = 100;
    public static final String PROMO = "SAVE5";

    public enum Build {
        CORRECT(true, true),
        /** Regression: the promo code is accepted but the total is not reduced. */
        PROMO_BROKEN(false, true),
        /** Regression: the payment is accepted but the order stays PENDING. */
        PAYMENT_BROKEN(true, false);

        final boolean promoWorks;
        final boolean paymentWorks;

        Build(boolean promoWorks, boolean paymentWorks) {
            this.promoWorks = promoWorks;
            this.paymentWorks = paymentWorks;
        }
    }

    private final Build build;
    private final AtomicLong ids = new AtomicLong();
    private final Map<Long, Order> orders = new HashMap<>();
    private final Map<String, String> savedCards = new HashMap<>();

    public FakeShop(Build build) {
        this.build = build;
    }

    public FakeShop() {
        this(Build.CORRECT);
    }

    /** A customer with a saved card. */
    public String registerCustomer(String name) {
        savedCards.put(name, "saved:" + name);
        return name;
    }

    public long createOrder(String customerOrNull) {
        long id = ids.incrementAndGet();
        orders.put(id, new Order(id, customerOrNull, CART_TOTAL, Order.Status.PENDING, null));
        return id;
    }

    public void applyPromo(long id, String code) {
        if (!PROMO.equals(code)) {
            throw new IllegalArgumentException("unknown promo code: " + code);
        }
        Order o = order(id);
        int total = build.promoWorks ? o.total() - 5 : o.total();
        orders.put(id, new Order(id, o.customer(), total, o.status(), o.paidWith()));
    }

    public void payWithCard(long id, String cardNumber) {
        markPaid(id, "card *" + cardNumber.substring(cardNumber.length() - 4));
    }

    public void payWithSavedCard(long id) {
        Order o = order(id);
        if (o.customer() == null) {
            throw new IllegalStateException("guest order " + id + " has no saved card");
        }
        markPaid(id, savedCards.get(o.customer()));
    }

    private void markPaid(long id, String paidWith) {
        Order o = order(id);
        if (build.paymentWorks) {
            orders.put(id, new Order(id, o.customer(), o.total(), Order.Status.PAID, paidWith));
        }
    }

    public Order order(long id) {
        Order o = orders.get(id);
        if (o == null) {
            throw new IllegalArgumentException("no order " + id);
        }
        return o;
    }

    public void delete(long id) {
        orders.remove(id);
    }

    public int orderCount() {
        return orders.size();
    }
}
