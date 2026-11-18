package com.study.principles.concurrency.futures;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Fake providers whose futures the test controls: answered, failed, late, or never. */
public final class Providers {

    private Providers() {
    }

    /** Returns the given future every time: the test completes it, fails it, or leaves it pending. */
    public static PriceProvider of(String name, CompletableFuture<Quote> answer) {
        return new PriceProvider() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public CompletableFuture<Quote> quote(String sku) {
                return answer;
            }
        };
    }

    public static PriceProvider answering(String name, long priceCents) {
        return of(name, CompletableFuture.completedFuture(new Quote(name, priceCents)));
    }

    public static PriceProvider never(String name) {
        return of(name, new CompletableFuture<>());
    }

    /** Answers on another thread after a real delay, as an HTTP client would. */
    public static PriceProvider after(String name, long priceCents, Duration delay) {
        return new PriceProvider() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public CompletableFuture<Quote> quote(String sku) {
                return CompletableFuture.supplyAsync(() -> new Quote(name, priceCents),
                        CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS));
            }
        };
    }
}
