package com.study.principles.srp;

import java.math.BigDecimal;

/** Fake REST client of the shop: what RestAssured calls would do. No browser involved. */
public final class ShopApi {

    private final FakeShop shop;

    public ShopApi(FakeShop shop) {
        this.shop = shop;
    }

    public void addToCart(String user, Item item) {
        shop.addToCart(user, item);
    }

    public BigDecimal cartTotal(String user) {
        return shop.total(user);
    }

    public String placeOrder(String user, String card) {
        return shop.placeOrder(user, card);
    }
}
