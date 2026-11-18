package com.study.principles.concurrency.futures;

/** One provider's price for a SKU, in cents. */
public record Quote(String provider, long priceCents) {
}
