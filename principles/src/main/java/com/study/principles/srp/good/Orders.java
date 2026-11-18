package com.study.principles.srp.good;

import com.study.principles.srp.FakeShop.Order;
import com.study.principles.srp.ShopDb;

/** Reason to change: the persistence schema. Returns the row; the test asserts on it. */
public final class Orders {

    private final ShopDb db;

    public Orders(ShopDb db) {
        this.db = db;
    }

    public Order get(String id) {
        return db.findOrder(id).orElseThrow(() -> new AssertionError("no order " + id + " in DB"));
    }
}
