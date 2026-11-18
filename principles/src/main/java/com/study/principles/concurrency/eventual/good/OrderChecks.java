package com.study.principles.concurrency.eventual.good;

import com.study.principles.concurrency.eventual.FakeOrderSystem;
import com.study.principles.concurrency.eventual.OrderView;
import com.study.principles.concurrency.eventual.OrderView.Status;
import com.study.principles.concurrency.eventual.ReadErrors;

import java.time.Duration;

/**
 * The checks of {@code bad.OrderChecks}, on top of {@link Poll}: a deadline, only 404 is "not yet", conditions that
 * stay true, and the matched value returned for the test to assert on.
 */
public final class OrderChecks {

    private final FakeOrderSystem system;
    private final Duration timeout;

    public OrderChecks(FakeOrderSystem system, Duration timeout) {
        this.system = system;
        this.timeout = timeout;
    }

    public OrderView orderVisible(String id) {
        return poll(id).until(order -> true);
    }

    /** "PAID or later": once true, stays true. Waiting for exactly PAID would race the warehouse. */
    public OrderView statusAtLeast(String id, Status status) {
        return poll(id).until(order -> order.status().isAtLeast(status));
    }

    private Poll<OrderView> poll(String id) {
        return Poll.of(id + " in the read model", () -> system.getOrder(id))
                .ignoring(ReadErrors.NotFound.class)
                .every(Duration.ofMillis(50))
                .atMost(timeout);
    }
}
