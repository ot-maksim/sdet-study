package com.study.principles.concurrency.events.ui;

import java.time.Duration;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The page's timers ({@code setTimeout}), shaped like Playwright's {@code page.clock()}.
 *
 * <p>By default timers fire in real time. After {@link #install()} they are fake and <b>paused</b>: a callback runs
 * only when the test moves time with {@link #runFor}. (In Playwright: {@code clock().install()} and then
 * {@code clock().pauseAt(..)}; {@code runFor} is the same.) Timers set before {@code install} keep real time:
 * install before the action.
 */
public final class PageClock {

    private record Timer(long dueNanos, long seq, Runnable task) {
    }

    private final ScheduledExecutorService browser;
    private final PriorityQueue<Timer> timers =
            new PriorityQueue<>((a, b) -> a.dueNanos != b.dueNanos ? Long.compare(a.dueNanos, b.dueNanos) : Long.compare(a.seq, b.seq));
    private volatile boolean installed;
    private long nowNanos;   // guarded by this
    private long seq;        // guarded by this

    PageClock(ScheduledExecutorService browser) {
        this.browser = browser;
    }

    /** Fake timers from now on, paused: nothing scheduled after this fires until {@link #runFor}. */
    public void install() {
        installed = true;
    }

    /** Moves fake time forward, running every timer that falls due, in order, on the browser thread. */
    public void runFor(Duration duration) {
        if (!installed) {
            throw new IllegalStateException("clock().install() first");
        }
        long target;
        synchronized (this) {
            target = nowNanos + duration.toNanos();
        }
        while (true) {
            Timer due;
            synchronized (this) {
                Timer next = timers.peek();
                if (next == null || next.dueNanos > target) {
                    nowNanos = target;
                    return;
                }
                due = timers.poll();
                nowNanos = due.dueNanos;
            }
            runOnBrowser(due.task);
        }
    }

    void setTimeout(Runnable task, Duration delay) {
        if (!installed) {
            browser.schedule(task, delay.toNanos(), TimeUnit.NANOSECONDS);
            return;
        }
        synchronized (this) {
            timers.add(new Timer(nowNanos + delay.toNanos(), seq++, task));
        }
    }

    private void runOnBrowser(Runnable task) {
        try {
            browser.submit(task).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("Timer callback failed", e);
        }
    }
}
