package com.study.principles.srp.good;

import com.study.principles.srp.Item;
import com.study.principles.srp.ShopApi;

import java.util.List;

/** Reason to change: how test state is set up (API today; DB insert or a fixture service tomorrow — see {@code dip}). */
public final class CartPrecondition {

    private final ShopApi api;

    public CartPrecondition(ShopApi api) {
        this.api = api;
    }

    public void fill(String user, List<Item> items) {
        items.forEach(item -> api.addToCart(user, item));
    }
}
