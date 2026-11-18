package com.study.principles.kiss.flags.good;

import com.study.principles.kiss.flags.FakeShop;

import java.util.ArrayList;
import java.util.List;

/**
 * Cleanup without a {@code cleanup} flag: every order the steps create is registered here, and the test's
 * lifecycle (an {@code @AfterEach} or an extension, see {@code coupling}) deletes them all — also when the test
 * failed half-way.
 */
public final class CreatedOrders {

    private final FakeShop shop;
    private final List<Long> ids = new ArrayList<>();

    public CreatedOrders(FakeShop shop) {
        this.shop = shop;
    }

    long register(long id) {
        ids.add(id);
        return id;
    }

    public void deleteAll() {
        ids.forEach(shop::delete);
        ids.clear();
    }
}
