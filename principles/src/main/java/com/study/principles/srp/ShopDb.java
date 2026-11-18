package com.study.principles.srp;

import java.util.Optional;

/** Fake JDBC access to the shop's {@code orders} table. */
public final class ShopDb {

    private final FakeShop shop;

    public ShopDb(FakeShop shop) {
        this.shop = shop;
    }

    public Optional<FakeShop.Order> findOrder(String id) {
        return shop.order(id);
    }
}
