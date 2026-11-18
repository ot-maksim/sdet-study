package com.study.principles.concurrency.futures.good;

import com.study.principles.concurrency.futures.BestQuote;
import com.study.principles.concurrency.futures.NoQuotesException;
import com.study.principles.concurrency.futures.PriceProvider;
import com.study.principles.concurrency.futures.Quote;
import com.study.principles.concurrency.futures.Timer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * The SUT: asks every provider at once and returns the cheapest quote. A provider that fails or does not answer within
 * the timeout is skipped, with the reason; if none answers, the future fails with every reason.
 *
 * <p>Designed to be testable:
 * <ul>
 *   <li><b>No threads of its own.</b> It only composes the providers' futures; the continuations run on whichever thread
 *       completes them: the provider's client, the timer, or the test that completes a future by hand.</li>
 *   <li><b>Time is injected</b> ({@link Timer}): the timeout path is tested without waiting.</li>
 *   <li><b>Every outcome is a value</b> (a quote or a reason): one provider's failure does not lose the others, and
 *       the reason survives to the result.</li>
 * </ul>
 */
public final class QuoteAggregator {

    private record Attempt(Quote quote, String skipped) {
    }

    private final List<PriceProvider> providers;
    private final Duration timeout;
    private final Timer timer;

    public QuoteAggregator(List<PriceProvider> providers, Duration timeout, Timer timer) {
        this.providers = List.copyOf(providers);
        this.timeout = timeout;
        this.timer = timer;
    }

    public CompletableFuture<BestQuote> bestQuote(String sku) {
        List<CompletableFuture<Attempt>> attempts = providers.stream().map(p -> attempt(p, sku)).toList();
        return CompletableFuture.allOf(attempts.toArray(CompletableFuture[]::new))
                .thenApply(done -> pick(attempts.stream().map(CompletableFuture::join).toList()));
    }

    private CompletableFuture<Attempt> attempt(PriceProvider provider, String sku) {
        CompletableFuture<Attempt> answer;
        try {
            answer = provider.quote(sku).handle((quote, error) -> error == null
                    ? new Attempt(quote, null)
                    : new Attempt(null, provider.name() + ": " + describe(error)));
        } catch (RuntimeException e) {   // a client that throws instead of returning a failed future
            answer = CompletableFuture.completedFuture(new Attempt(null, provider.name() + ": " + describe(e)));
        }
        CompletableFuture<Attempt> late = timer.after(timeout)
                .thenApply(elapsed -> new Attempt(null, provider.name() + ": timeout after " + timeout));
        return answer.applyToEither(late, attempt -> attempt);
    }

    private static BestQuote pick(List<Attempt> attempts) {
        List<String> skipped = new ArrayList<>();
        Quote best = null;
        for (Attempt attempt : attempts) {
            if (attempt.quote() == null) {
                skipped.add(attempt.skipped());
            } else if (best == null || Comparator.comparingLong(Quote::priceCents).compare(attempt.quote(), best) < 0) {
                best = attempt.quote();
            }
        }
        if (best == null) {
            throw new NoQuotesException(skipped);
        }
        return new BestQuote(best, skipped);
    }

    private static String describe(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
