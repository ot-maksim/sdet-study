package com.study.principles.concurrency.futures.good;

import com.study.principles.concurrency.futures.BestQuote;
import com.study.principles.concurrency.futures.ManualTimer;
import com.study.principles.concurrency.futures.NoQuotesException;
import com.study.principles.concurrency.futures.PriceProvider;
import com.study.principles.concurrency.futures.Providers;
import com.study.principles.concurrency.futures.Quote;
import com.study.principles.concurrency.futures.Timer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.study.principles.concurrency.futures.Providers.answering;
import static com.study.principles.concurrency.futures.Providers.never;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testing code that returns {@code CompletableFuture}: the test owns every future and the time, so most tests run on
 * one thread, in a known order, without waiting. Where real threads are involved, it waits with a deadline.
 */
class GoodFuturesTest {

    private final ManualTimer timer = new ManualTimer();

    private QuoteAggregator aggregator(PriceProvider... providers) {
        return new QuoteAggregator(List.of(providers), Duration.ofSeconds(2), timer);
    }

    // --- 1. The test controls completion --------------------------------------------------------------------

    @Test
    void completedProviders_theResultIsReadyAtOnce() {
        CompletableFuture<BestQuote> result = aggregator(answering("A", 500), answering("B", 300), answering("C", 400))
                .bestQuote("sku-1");

        assertThat(result).isCompletedWithValueMatching(best -> best.best().equals(new Quote("B", 300)));
    }

    @Test
    void theResultWaitsForEveryProvider_whateverOrderTheyAnswerIn() {
        CompletableFuture<Quote> a = new CompletableFuture<>();
        CompletableFuture<Quote> b = new CompletableFuture<>();
        CompletableFuture<Quote> c = new CompletableFuture<>();
        CompletableFuture<BestQuote> result = aggregator(Providers.of("A", a), Providers.of("B", b), Providers.of("C", c))
                .bestQuote("sku-1");

        c.complete(new Quote("C", 400));
        b.complete(new Quote("B", 300));
        assertThat(result).isNotDone();

        a.complete(new Quote("A", 250));   // the last one is the cheapest
        assertThat(result).isCompletedWithValueMatching(best -> best.best().provider().equals("A"));
    }

    // --- 2. Unhappy paths, without waiting -------------------------------------------------------------------

    @Test
    void aProviderThatNeverAnswers_isSkippedAtTheTimeout_testedWithoutWaitingForIt() {
        long start = System.nanoTime();
        CompletableFuture<BestQuote> result = aggregator(answering("A", 500), answering("B", 300), never("C"))
                .bestQuote("sku-1");
        assertThat(result).isNotDone();

        timer.fireAll();   // two seconds pass, now

        assertThat(result).isCompletedWithValueMatching(best -> best.best().provider().equals("B")
                && best.skipped().equals(List.of("C: timeout after PT2S")));
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(100));
    }

    @Test
    void aFailingProvider_isSkippedWithItsReason() {
        CompletableFuture<BestQuote> result = aggregator(answering("A", 500),
                Providers.of("B", CompletableFuture.failedFuture(new IOException("connection reset"))))
                .bestQuote("sku-1");

        assertThat(result).isCompletedWithValueMatching(best -> best.skipped().equals(List.of("B: IOException: connection reset")));
    }

    @Test
    void aClientThatThrowsInsteadOfFailingTheFuture_isSkippedToo() {
        PriceProvider throwing = new PriceProvider() {
            @Override
            public String name() {
                return "B";
            }

            @Override
            public CompletableFuture<Quote> quote(String sku) {
                throw new IllegalArgumentException("unknown SKU format");
            }
        };

        assertThat(aggregator(answering("A", 500), throwing).bestQuote("sku-1"))
                .isCompletedWithValueMatching(best -> best.skipped().equals(List.of("B: IllegalArgumentException: unknown SKU format")));
    }

    @Test
    void noProviderAnswers_theFutureFailsWithEveryReason() {
        CompletableFuture<BestQuote> result = aggregator(never("A"),
                Providers.of("B", CompletableFuture.failedFuture(new IOException("connection reset"))))
                .bestQuote("sku-1");
        timer.fireAll();

        assertThat(result).isCompletedExceptionally();
        assertThatThrownBy(() -> result.get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause().isInstanceOf(NoQuotesException.class)
                .hasMessage("No quotes: [A: timeout after PT2S, B: IOException: connection reset]");
    }

    // --- 3. Real threads: wait with a deadline, assert on the value --------------------------------------------

    @Test
    void aRealAsyncProvider_succeedsWithinADeadline() {
        QuoteAggregator real = new QuoteAggregator(
                List.of(Providers.after("A", 500, Duration.ofMillis(50)), Providers.after("B", 300, Duration.ofMillis(80))),
                Duration.ofSeconds(2), Timer.system());

        assertThat(real.bestQuote("sku-1")).succeedsWithin(Duration.ofSeconds(1))
                .satisfies(best -> assertThat(best.best()).isEqualTo(new Quote("B", 300)));
    }

    @Test
    void theSystemTimer_reallyWaits() {
        long start = System.nanoTime();

        assertThat(Timer.system().after(Duration.ofMillis(100))).succeedsWithin(Duration.ofSeconds(1));
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(100));
    }
}
