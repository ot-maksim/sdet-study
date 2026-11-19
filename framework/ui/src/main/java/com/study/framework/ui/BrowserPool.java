package com.study.framework.ui;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Browsers for parallel UI tests. Playwright Java is not thread-safe: a {@code Playwright} and everything it
 * created must be used by one thread at a time. So a browser is never shared — a test <b>leases</b> a whole
 * {@code Playwright + Browser} exclusively for its duration and gives it back (principles/parallel: a
 * {@code BlockingQueue} pool). Hand-over through the queue makes the previous user's actions visible to the
 * next thread (happens-before).
 *
 * <p>Lazy: browsers are launched on demand up to {@code ui.max-browsers}; a run of API tests launches none.
 * Run-scoped; closing the pool closes every browser.
 */
public final class BrowserPool implements AutoCloseable {

    private static final long LEASE_WAIT_SECONDS = 120;

    private final UiSettings settings;
    private final BlockingQueue<Worker> idle = new LinkedBlockingQueue<>();
    private final List<Worker> all = new ArrayList<>();
    private final AtomicInteger inUse = new AtomicInteger();
    private final AtomicInteger maxInUse = new AtomicInteger();
    private final AtomicInteger launched = new AtomicInteger();
    private boolean closed;

    public BrowserPool(UiSettings settings) {
        this.settings = settings;
    }

    /** A browser for this test only; close the lease (via {@code Cleanup}) to give it back. */
    public Lease lease() {
        Worker worker = idle.poll();
        if (worker == null) {
            worker = launchIfBelowLimit();
        }
        if (worker == null) {
            try {
                worker = idle.poll(LEASE_WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for a browser", e);
            }
            if (worker == null) {
                throw new IllegalStateException("All " + settings.maxBrowsers() + " browsers stayed busy for "
                        + LEASE_WAIT_SECONDS + "s: a test did not give its lease back, or ui.max-browsers is below the parallelism");
            }
        }
        int now = inUse.incrementAndGet();
        maxInUse.accumulateAndGet(now, Math::max);
        return new Lease(worker);
    }

    /** Browsers launched by this pool, also after it was closed. */
    public int launched() {
        return launched.get();
    }

    /** The most leases held at the same time — proof that tests really overlapped. */
    public int maxInUse() {
        return maxInUse.get();
    }

    private synchronized Worker launchIfBelowLimit() {
        if (closed) {
            throw new IllegalStateException("Browser pool is closed");
        }
        if (all.size() >= settings.maxBrowsers()) {
            return null;
        }
        Worker worker = Worker.launch(settings);
        all.add(worker);
        launched.incrementAndGet();
        return worker;
    }

    @Override
    public void close() {
        List<Worker> toClose;
        synchronized (this) {
            closed = true;
            toClose = new ArrayList<>(all);
            all.clear();
        }
        RuntimeException failure = null;
        for (Worker worker : toClose) {
            try {
                worker.close();
            } catch (RuntimeException e) {
                failure = failure == null ? e : failure;
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** One browser held exclusively by one test. */
    public final class Lease implements AutoCloseable {

        private final Worker worker;
        private boolean returned;

        private Lease(Worker worker) {
            this.worker = worker;
        }

        public Browser browser() {
            return worker.browser;
        }

        @Override
        public void close() {
            if (!returned) {
                returned = true;
                inUse.decrementAndGet();
                idle.add(worker);
            }
        }
    }

    private static final class Worker implements AutoCloseable {

        private final Playwright playwright;
        private final Browser browser;

        private Worker(Playwright playwright, Browser browser) {
            this.playwright = playwright;
            this.browser = browser;
        }

        static Worker launch(UiSettings settings) {
            // Browsers are installed once, explicitly (README); never downloaded behind a test's back
            Playwright playwright = Playwright.create(new Playwright.CreateOptions()
                    .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
            try {
                Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(settings.headless()));
                return new Worker(playwright, browser);
            } catch (RuntimeException e) {
                playwright.close();
                throw new IllegalStateException("Cannot launch Chromium. Installed? See framework/ui/README.md "
                        + "(Playwright CLI: install chromium). Cause: " + e.getMessage(), e);
            }
        }

        @Override
        public void close() {
            playwright.close();   // closes the browser too
        }
    }
}
