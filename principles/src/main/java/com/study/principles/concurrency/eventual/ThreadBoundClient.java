package com.study.principles.concurrency.eventual;

/**
 * A client that may only be used on the thread that created it, like a Playwright {@code Page} (not thread-safe, bound
 * to its thread) or a client keeping per-test state in a {@code ThreadLocal} (auth, a request log for the report).
 * A poll that runs its condition on another thread breaks it.
 */
public final class ThreadBoundClient {

    private final FakeOrderSystem system;
    private final Thread owner = Thread.currentThread();

    public ThreadBoundClient(FakeOrderSystem system) {
        this.system = system;
    }

    public OrderView getOrder(String id) {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("Client created on '" + owner.getName()
                    + "' used from '" + Thread.currentThread().getName() + "'");
        }
        return system.getOrder(id);
    }
}
