package com.study.template.events;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A listener that can be switched off reliably — moved unchanged from the kata
 * ({@code principles/concurrency/events}).
 *
 * <p>{@link EventClient} dispatches over a {@code CopyOnWriteArrayList} snapshot, so an unsubscribed listener can
 * still get one more call from the dispatch in progress. After {@link #deactivate()} returns, the delegate is not
 * running and never runs again. {@code accept} and {@code deactivate} share one monitor: a {@code volatile} flag
 * alone leaves a check-then-act race. The cost: a slow delegate blocks {@code deactivate}.
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
