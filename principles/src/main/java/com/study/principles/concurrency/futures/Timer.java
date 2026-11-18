package com.study.principles.concurrency.futures;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Time as a dependency of async code: "a future that completes after {@code delay}". Production uses {@link #system()};
 * a test passes a timer it fires by hand, so the timeout path runs in microseconds and in a known order (the same move
 * as injecting a {@code Clock}, see {@code principles/time}).
 */
public interface Timer {

    CompletableFuture<Void> after(Duration delay);

    static Timer system() {
        return delay -> CompletableFuture.runAsync(() -> { },
                CompletableFuture.delayedExecutor(delay.toNanos(), TimeUnit.NANOSECONDS));
    }
}
