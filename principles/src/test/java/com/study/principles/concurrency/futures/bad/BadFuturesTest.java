package com.study.principles.concurrency.futures.bad;

import com.study.principles.concurrency.futures.BestQuote;
import com.study.principles.concurrency.futures.ManualTimer;
import com.study.principles.concurrency.futures.Providers;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.study.principles.concurrency.futures.Providers.answering;
import static com.study.principles.concurrency.futures.Providers.never;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Characterization tests. Two kinds of smell: <b>tests</b> of async code written badly (against the good aggregator),
 * and an async <b>SUT</b> designed so that it cannot be tested well ({@link QuoteAggregator} of this package).
 */
class BadFuturesTest {

    private static com.study.principles.concurrency.futures.good.QuoteAggregator good(ManualTimer timer,
            com.study.principles.concurrency.futures.PriceProvider... providers) {
        return new com.study.principles.concurrency.futures.good.QuoteAggregator(List.of(providers), Duration.ofSeconds(2), timer);
    }

    // --- 1. Bad tests of async code ---------------------------------------------------------------------------

    @Test
    void assertionInsideThenAccept_isSwallowed_andTheTestIsGreen() {
        var aggregator = good(new ManualTimer(), answering("A", 500), answering("B", 300));

        CompletableFuture<Void> check = aggregator.bestQuote("sku-1")
                .thenAccept(best -> assertThat(best.best().priceCents()).isEqualTo(999));   // wrong, and nobody hears it

        // A test that ends at the line above passes. The AssertionError is inside a future nobody joins:
        assertThat(check).isCompletedExceptionally();
    }

    @Test
    void joinWithoutATimeout_hangsWhenAProviderNeverAnswers() {
        var aggregator = good(new ManualTimer(), answering("A", 500), never("C"));   // nobody fires the timer

        CompletableFuture<BestQuote> result = aggregator.bestQuote("sku-1");
        CompletableFuture<BestQuote> theTest = CompletableFuture.supplyAsync(result::join);

        assertThatThrownBy(() -> theTest.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        // In a real suite there is no outer get(300 ms): the build hangs until CI kills it.
    }

    @Test
    void sleepThenIsDone_checksTheSchedulerNotTheCode() throws InterruptedException {
        var aggregator = new com.study.principles.concurrency.futures.good.QuoteAggregator(
                List.of(Providers.after("A", 500, Duration.ofMillis(150))), Duration.ofSeconds(2),
                com.study.principles.concurrency.futures.Timer.system());

        CompletableFuture<BestQuote> result = aggregator.bestQuote("sku-1");
        Thread.sleep(50);   // "should be enough"

        assertThat(result.isDone()).isFalse();
    }

    // --- 2. An async SUT that resists testing --------------------------------------------------------------------

    @Test
    void hardWiredRealTimeout_everyTestOfTheTimeoutPathWaitsForIt_andTheReasonIsLost() throws Exception {
        var aggregator = new QuoteAggregator(List.of(answering("A", 500), never("C")));
        long start = System.nanoTime();

        BestQuote best = aggregator.bestQuote("sku-1").get(5, TimeUnit.SECONDS);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(500));
        assertThat(best.skipped()).as("why C did not count").isEmpty();
    }

    @Test
    void aPoolPerInstance_neverShutDown_leaksThreads() throws Exception {
        long before = quotePoolThreads();

        for (int i = 0; i < 5; i++) {
            new QuoteAggregator(List.of(answering("A", 500), answering("B", 300), answering("C", 400)))
                    .bestQuote("sku-1").get(5, TimeUnit.SECONDS);
        }

        assertThat(quotePoolThreads() - before).as("threads left behind by 5 aggregators").isGreaterThanOrEqualTo(5);
    }

    private static long quotePoolThreads() {
        return Thread.getAllStackTraces().keySet().stream().filter(t -> t.getName().startsWith("quote-pool-")).count();
    }
}
