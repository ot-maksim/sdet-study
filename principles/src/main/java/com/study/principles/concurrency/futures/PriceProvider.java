package com.study.principles.concurrency.futures;

import java.util.concurrent.CompletableFuture;

/** A downstream price service with an async client: the call returns at once, the answer comes later (or never). */
public interface PriceProvider {

    String name();

    CompletableFuture<Quote> quote(String sku);
}
