package com.study.principles.concurrency.events;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A listener that can be switched off reliably.
 *
 * <p>Why: {@link EventClient} dispatches over a {@code CopyOnWriteArrayList} snapshot, so a listener that
 * was already unsubscribed can still get one more call from the dispatch in progress. After
 * {@link #deactivate()} returns, the delegate is not running and will never run again: resources it uses
 * (files, reports, mocks) can be released.
 *
 * <p>{@code accept} and {@code deactivate} synchronize on the same monitor, so {@code deactivate} waits for
 * a running {@code accept} to finish. A {@code volatile} flag alone does not do that: the check and the
 * call are two steps, and {@code deactivate} can slip in between them (see {@code events.kafka.bad.VolatileSwitchableListener}).
 * The cost: a slow delegate blocks {@code deactivate}. Fine for tests; think twice in production code.
 */
public final class SwitchableListener<T> implements Consumer<T> {

    private final Consumer<T> delegate;
    private boolean active = true;   // visibility comes from synchronized

    public SwitchableListener(Consumer<T> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public synchronized void accept(T event) {
        if (active) {
            delegate.accept(event);
        }
    }

    public synchronized void deactivate() {
        active = false;
    }
}
