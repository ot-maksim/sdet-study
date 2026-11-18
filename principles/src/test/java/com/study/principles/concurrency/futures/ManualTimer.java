package com.study.principles.concurrency.futures;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/** A {@link Timer} the test fires by hand: "every timeout elapses now", on the test thread. */
public final class ManualTimer implements Timer {

    private final List<CompletableFuture<Void>> pending = new CopyOnWriteArrayList<>();

    @Override
    public CompletableFuture<Void> after(Duration delay) {
        CompletableFuture<Void> elapsed = new CompletableFuture<>();
        pending.add(elapsed);
        return elapsed;
    }

    public void fireAll() {
        pending.forEach(elapsed -> elapsed.complete(null));
    }
}
