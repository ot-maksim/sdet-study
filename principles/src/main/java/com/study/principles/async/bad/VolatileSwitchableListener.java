package com.study.principles.async.bad;

import java.util.function.Consumer;

/**
 * The "obvious" switchable listener: a {@code volatile} flag. Visibility is fine, atomicity is not.
 *
 * <pre>
 * poll thread:  if (active)  ──────────────►  delegate.accept(event)  // writes to the report
 * test thread:            deactivate(); report.close();
 * </pre>
 *
 * {@code deactivate()} returns while the delegate is still running (or about to run), so "switched off"
 * does not mean "safe to release resources". See {@code good.SwitchableListener}.
 */
public final class VolatileSwitchableListener<T> implements Consumer<T> {

    private final Consumer<T> delegate;
    private volatile boolean active = true;

    public VolatileSwitchableListener(Consumer<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void accept(T event) {
        if (active) {               // check...
            delegate.accept(event); // ...then act: deactivate() may have returned in between
        }
    }

    public void deactivate() {
        active = false;
    }
}
