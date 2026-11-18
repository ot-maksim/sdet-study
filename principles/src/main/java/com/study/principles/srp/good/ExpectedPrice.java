package com.study.principles.srp.good;

import com.study.principles.srp.Item;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The test oracle. Reason to change: the <b>pricing rule</b> (product team). A pure function: no browser,
 * no API, so it is checked against the SUT for thousands of carts in milliseconds and reused by UI, API and
 * DB tests alike. Written from the spec independently of the SUT code — copying the SUT's code into the
 * oracle would make it agree with every bug.
 */
public final class ExpectedPrice {

    static final BigDecimal DISCOUNT_THRESHOLD = new BigDecimal("100");
    static final BigDecimal DISCOUNT = new BigDecimal("0.10");
    static final BigDecimal VAT = new BigDecimal("0.20");

    private ExpectedPrice() {
    }

    public static BigDecimal of(List<Item> items) {
        BigDecimal subtotal = items.stream()
                .map(item -> item.price().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discounted = subtotal.compareTo(DISCOUNT_THRESHOLD) >= 0
                ? subtotal.subtract(subtotal.multiply(DISCOUNT))
                : subtotal;
        return discounted.add(discounted.multiply(VAT)).setScale(2, RoundingMode.HALF_UP);
    }
}
