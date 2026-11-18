package com.study.principles.concurrency.eventual;

import com.study.principles.concurrency.eventual.OrderView.Status;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * The SUT: CQRS with an asynchronous projection. Commands ({@link #placeOrder}, {@link #pay}) are accepted at once;
 * the read side ({@link #getOrder}, {@link #search}) shows them <b>after the projection lag</b>, applied by a
 * background thread in order. Paying also triggers shipping, so {@code PAID} may be visible only for a moment, or not
 * at all: when both changes are due together, the projector applies them in one batch and the read side jumps
 * straight from {@code NEW} to {@code SHIPPED}.
 *
 * <p>No replay problems here, no lost events: the state <b>will</b> be there. The only question for the test is how to
 * wait for it.
 */
public final class FakeOrderSystem implements AutoCloseable {

    private record Change(long dueNanos, String id, String customer, Status status) {
    }

    private final Supplier<Duration> projectionLag;
    private final Duration shippingDelay;
    private final Queue<Change> pending = new ArrayDeque<>();          // guarded by this
    private final Map<String, OrderView> readModel = new HashMap<>();   // guarded by this
    private final Map<String, String> customers = new HashMap<>();      // guarded by this
    private final AtomicLong ids = new AtomicLong();
    private final AtomicInteger reads = new AtomicInteger();
    private final Thread projector;
    private volatile RuntimeException readFailure;

    public FakeOrderSystem(Duration projectionLag, Duration shippingDelay) {
        this(() -> projectionLag, shippingDelay);
    }

    public FakeOrderSystem(Supplier<Duration> projectionLag, Duration shippingDelay) {
        this.projectionLag = projectionLag;
        this.shippingDelay = shippingDelay;
        this.projector = new Thread(this::project, "order-projector");
        this.projector.setDaemon(true);
        this.projector.start();
    }

    // --- write side: accepted at once -------------------------------------------------------------

    public String placeOrder(String customer) {
        String id = "order-" + ids.incrementAndGet();
        synchronized (this) {
            customers.put(id, customer);
            enqueue(id, Status.NEW, Duration.ZERO);
        }
        return id;
    }

    public void pay(String id) {
        synchronized (this) {
            enqueue(id, Status.PAID, Duration.ZERO);
            enqueue(id, Status.SHIPPED, shippingDelay);   // the warehouse reacts to the payment
        }
    }

    // --- read side: eventually ---------------------------------------------------------------------

    /** {@code GET /orders/{id}}: 404 until the projection has caught up. */
    public OrderView getOrder(String id) {
        reads.incrementAndGet();
        failIfBroken();
        synchronized (this) {
            OrderView view = readModel.get(id);
            if (view == null) {
                throw new ReadErrors.NotFound(id);
            }
            return view;
        }
    }

    /** {@code GET /orders?customer=..}: what the read model has right now. */
    public List<OrderView> search(String customer) {
        reads.incrementAndGet();
        failIfBroken();
        synchronized (this) {
            return readModel.values().stream().filter(v -> v.customer().equals(customer)).toList();
        }
    }

    public Optional<OrderView> find(String id) {
        try {
            return Optional.of(getOrder(id));
        } catch (ReadErrors.NotFound e) {
            return Optional.empty();
        }
    }

    /** From now on every read fails with this error: an expired token, a misconfigured environment. */
    public void breakReads(RuntimeException failure) {
        this.readFailure = failure;
    }

    /** How many read requests the tests made: the load a poll puts on the SUT. */
    public int reads() {
        return reads.get();
    }

    @Override
    public void close() {
        projector.interrupt();
    }

    private void failIfBroken() {
        RuntimeException failure = readFailure;
        if (failure != null) {
            throw failure;
        }
    }

    /** Called under the monitor. */
    private void enqueue(String id, Status status, Duration extraDelay) {
        long due = System.nanoTime() + projectionLag.get().toNanos() + extraDelay.toNanos();
        pending.add(new Change(due, id, customers.get(id), status));
        notifyAll();
    }

    private void project() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                synchronized (this) {
                    Change next = pending.peek();
                    long wait = next == null ? 0 : next.dueNanos() - System.nanoTime();
                    if (next == null || wait > 0) {
                        if (next == null) {
                            wait();
                        } else {
                            wait(Math.max(1, wait / 1_000_000), (int) (wait % 1_000_000));
                        }
                        continue;
                    }
                    // Apply everything that is due, in one step: readers see the batch or nothing of it.
                    List<Change> batch = new ArrayList<>();
                    while (!pending.isEmpty() && pending.peek().dueNanos() <= System.nanoTime()) {
                        batch.add(pending.poll());
                    }
                    for (Change change : batch) {
                        readModel.put(change.id(), new OrderView(change.id(), change.customer(), change.status()));
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
