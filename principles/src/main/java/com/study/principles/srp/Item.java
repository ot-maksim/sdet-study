package com.study.principles.srp;

import java.math.BigDecimal;

public record Item(String sku, BigDecimal price, int quantity) {

    public static Item of(String sku, String price, int quantity) {
        return new Item(sku, new BigDecimal(price), quantity);
    }
}
