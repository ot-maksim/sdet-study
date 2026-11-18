package com.study.principles.concurrency.fanout;

import java.util.List;

/** The preconditions of an order test: four independent entities, created at once. */
public record Fixture(String user, String product, String stock, String promo) {

    public static final List<String> TYPES = List.of("user", "product", "stock", "promo");
}
