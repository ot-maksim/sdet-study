package com.study.principles.concurrency.futures.bad;

import com.study.principles.concurrency.futures.BestQuote;
import com.study.principles.concurrency.futures.NoQuotesException;
import com.study.principles.concurrency.futures.PriceProvider;
import com.study.principles.concurrency.futures.Quote;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The same aggregator, written so that it works and cannot be tested well:
 * <ul>
 *   <li><b>A pool per instance, never shut down.</b> Every {@code new QuoteAggregator} leaves three threads behind
 *       (here daemon threads, so the demo can exit; with the default factory they are not, and the JVM never exits).</li>
 *   <li><b>{@code supplyAsync(() -> provider.quote().join())}</b>: wraps an async call into a blocking one on a pool thread.
 *       When a provider never answers, {@code orTimeout} completes the outer future, but the pool thread stays blocked
 *       in {@code join()} forever.</li>
 *   <li><b>A hard-coded real-time timeout (500 ms).</b> Every test of the timeout path waits for it.</li>
 *   <li><b>{@code exceptionally(e -> null)}</b>: a timeout and an error both become "no quote", and the reason is gone.</li>
 * </ul>
 */
public final class QuoteAggregator {

    private static final AtomicInteger threadIds = new AtomicInteger();

    private final List<PriceProvider> providers;
    private final ExecutorService pool = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "quote-pool-" + threadIds.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    public QuoteAggregator(List<PriceProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public CompletableFuture<BestQuote> bestQuote(String sku) {
        List<CompletableFuture<Quote>> quotes = providers.stream()
                .map(provider -> CompletableFuture.supplyAsync(() -> provider.quote(sku).join(), pool)
                        .orTimeout(500, TimeUnit.MILLISECONDS)
                        .exceptionally(error -> null))
                .toList();
        return CompletableFuture.allOf(quotes.toArray(CompletableFuture[]::new))
                .thenApply(done -> quotes.stream().map(CompletableFuture::join).filter(Objects::nonNull)
                        .min(Comparator.comparingLong(Quote::priceCents))
                        .map(best -> new BestQuote(best, List.of()))
                        .orElseThrow(() -> new NoQuotesException(List.of())));
    }
}
