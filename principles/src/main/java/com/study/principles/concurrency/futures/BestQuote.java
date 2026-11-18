package com.study.principles.concurrency.futures;

import java.util.List;

/** The cheapest quote, and why the other providers did not count (timeout, error). */
public record BestQuote(Quote best, List<String> skipped) {

    public BestQuote {
        skipped = List.copyOf(skipped);
    }
}
