package com.study.principles.concurrency.eventual.bad;

import com.study.principles.concurrency.eventual.FakeOrderSystem;
import com.study.principles.concurrency.eventual.OrderView;
import com.study.principles.concurrency.eventual.OrderView.Status;

import java.time.Duration;

/**
 * Hand-rolled waiting, as it grows in a framework one bug report at a time:
 * <ul>
 *   <li>{@link #waitForOrder}: no deadline, and {@code catch (Exception)} around the read. A 404 ("not yet") and a
 *       401 ("never") look the same: with a broken token the loop spins until the CI job's own timeout, and the
 *       report says "job cancelled", not "401".</li>
 *   <li>{@link #waitForStatus}: waits for an <b>exact intermediate</b> state. If the read side moves past it between
 *       two polls (or never shows it), the wait fails although the system did everything right.</li>
 *   <li>Both return {@code void}/{@code boolean}: the caller reads the order again to assert on it, and may get a
 *       newer state than the one the wait saw.</li>
 * </ul>
 */
public final class OrderChecks {

    private static final Duration POLL = Duration.ofMillis(50);

    private final FakeOrderSystem system;

    public OrderChecks(FakeOrderSystem system) {
        this.system = system;
    }

    public void waitForOrder(String id) throws InterruptedException {
        while (true) {
            try {
                system.getOrder(id);
                return;
            } catch (Exception ignored) {
                // "not there yet"
            }
            Thread.sleep(POLL.toMillis());
        }
    }

    public boolean waitForStatus(String id, Status expected, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            OrderView view = system.find(id).orElse(null);
            if (view != null && view.status() == expected) {
                return true;
            }
            Thread.sleep(POLL.toMillis());
        }
        return false;
    }
}
