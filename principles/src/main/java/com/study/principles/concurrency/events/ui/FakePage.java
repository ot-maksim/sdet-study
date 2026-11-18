package com.study.principles.concurrency.events.ui;

import java.lang.System.Logger;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A browser page with the <b>shape of Playwright's {@code Page}</b>, so that the patterns carry over one to one.
 * The page's "JavaScript" (click handlers, network callbacks, timers) runs on its own thread, {@code browser-main};
 * the test drives it from the test thread. Things happen on the page <b>while the test is doing something else</b>.
 *
 * <p>Test-facing (as in Playwright):
 * <ul>
 *   <li>{@link #click}: returns when the click handler has run, not when its consequences (requests, renders) are done;</li>
 *   <li>{@link #textContent}, {@link #isVisible}: <b>snapshots</b>, no waiting (like Playwright's {@code isVisible()});</li>
 *   <li>{@link #waitForResponse(Predicate, Runnable)}: arms the waiter, runs the action, waits ("run and wait");</li>
 *   <li>{@link #onDialog}, {@link #onceDialog}: with no handler, a dialog is <b>dismissed automatically</b>;</li>
 *   <li>{@link #clock()}: fake timers; {@link #waitForCondition}: re-checked on every DOM change;</li>
 *   <li>{@link #screenshot()}: takes time, as on a real page (a step reporter calls it after every action).</li>
 * </ul>
 * App-facing (the page's own scripts, see {@link CheckoutApp}): {@link #onClick}, {@link #setText}, {@link #remove},
 * {@link #dialog}, {@link #fetch}, {@link #setTimeout}.
 */
public final class FakePage implements AutoCloseable {

    private static final Logger log = System.getLogger(FakePage.class.getName());

    private record ResponseWaiter(Predicate<Response> predicate, CompletableFuture<Response> future) {
    }

    private final ScheduledExecutorService browser = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "browser-main");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, String> dom = new HashMap<>();   // guarded by itself; notifyAll on every change
    private final Map<String, Runnable> clickHandlers = new ConcurrentHashMap<>();
    private final List<Consumer<Dialog>> dialogHandlers = new CopyOnWriteArrayList<>();
    private final List<ResponseWaiter> responseWaiters = new CopyOnWriteArrayList<>();
    private final PageClock clock = new PageClock(browser);
    private final Duration screenshotTime;
    private volatile Duration defaultTimeout = Duration.ofSeconds(5);

    public FakePage(Duration screenshotTime) {
        this.screenshotTime = screenshotTime;
    }

    // ---------------------------------------------------------------------------------------------
    // Test-facing API
    // ---------------------------------------------------------------------------------------------

    public void setDefaultTimeout(Duration timeout) {
        this.defaultTimeout = timeout;
    }

    public Duration defaultTimeout() {
        return defaultTimeout;
    }

    /** Runs the element's click handler on the browser thread and returns when the handler is done. */
    public void click(String selector) {
        Runnable handler = clickHandlers.get(selector);
        if (handler == null) {
            throw new TimeoutError("No clickable element " + selector);
        }
        try {
            browser.submit(handler).get(defaultTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Click handler of " + selector + " failed", e.getCause());
        } catch (TimeoutException e) {
            throw new TimeoutError("Timeout " + defaultTimeout + " exceeded while clicking " + selector);
        }
    }

    /** A snapshot: the text right now, {@code null} if the element is not on the page. */
    public String textContent(String selector) {
        synchronized (dom) {
            return dom.get(selector);
        }
    }

    /** A snapshot: does not wait. */
    public boolean isVisible(String selector) {
        return textContent(selector) != null;
    }

    /** Takes {@code screenshotTime}; the page keeps living meanwhile. */
    public byte[] screenshot() {
        try {
            Thread.sleep(screenshotTime.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new byte[0];
    }

    /** Run and wait: the waiter is armed <b>before</b> {@code action} runs, so a fast response is not missed. */
    public Response waitForResponse(Predicate<Response> predicate, Runnable action) {
        ResponseWaiter waiter = new ResponseWaiter(predicate, new CompletableFuture<>());
        responseWaiters.add(waiter);
        try {
            action.run();
            return waiter.future().get(defaultTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        } catch (TimeoutException e) {
            throw new TimeoutError("Timeout " + defaultTimeout + " exceeded while waiting for a response");
        } finally {
            responseWaiters.remove(waiter);
        }
    }

    /** A handler for every dialog from now on. */
    public void onDialog(Consumer<Dialog> handler) {
        dialogHandlers.add(handler);
    }

    /** A handler for the next dialog only. */
    public void onceDialog(Consumer<Dialog> handler) {
        dialogHandlers.add(new Consumer<>() {
            @Override
            public void accept(Dialog dialog) {
                dialogHandlers.remove(this);
                handler.accept(dialog);
            }
        });
    }

    public PageClock clock() {
        return clock;
    }

    /** Waits until the condition over the DOM holds, re-checking on every DOM change; throws on timeout. */
    public void waitForCondition(BooleanSupplier condition, Duration timeout) {
        if (!awaitDom(condition, timeout)) {
            throw new TimeoutError("Timeout " + timeout + " exceeded while waiting for a condition");
        }
    }

    boolean awaitDom(BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        synchronized (dom) {
            while (!condition.getAsBoolean()) {
                long leftMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (leftMillis <= 0) {
                    return false;
                }
                try {
                    dom.wait(leftMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    public void close() {
        browser.shutdownNow();
    }

    // ---------------------------------------------------------------------------------------------
    // App-facing API: the page's own scripts, all on the browser thread
    // ---------------------------------------------------------------------------------------------

    public void onClick(String selector, Runnable handler) {
        clickHandlers.put(selector, handler);
        setText(selector, selector);
    }

    public void setText(String selector, String text) {
        synchronized (dom) {
            dom.put(selector, text);
            dom.notifyAll();
        }
    }

    public void remove(String selector) {
        synchronized (dom) {
            dom.remove(selector);
            dom.notifyAll();
        }
    }

    /**
     * Shows a dialog and blocks the script until it is handled. No handler: dismissed automatically, as Playwright does.
     * A handler that neither accepts nor dismisses would hang a real page; here it is dismissed and logged.
     */
    public boolean dialog(String message) {
        Dialog dialog = new Dialog(message);
        for (Consumer<Dialog> handler : dialogHandlers) {
            try {
                handler.accept(dialog);
            } catch (RuntimeException e) {
                log.log(Logger.Level.WARNING, "Dialog handler failed: {0}", e.toString());
            }
            if (dialog.handled()) {
                break;
            }
        }
        if (!dialog.handled()) {
            if (!dialogHandlers.isEmpty()) {
                log.log(Logger.Level.WARNING, "Dialog \"{0}\" not handled by its handler", message);
            }
            dialog.dismiss();
        }
        return dialog.accepted();
    }

    /** Sends a request; after the latency the response reaches the waiters, then the page's callback. */
    public void fetch(Response response, Duration latency, Consumer<Response> then) {
        browser.schedule(() -> {
            for (ResponseWaiter waiter : responseWaiters) {
                if (waiter.predicate().test(response)) {
                    waiter.future().complete(response);
                }
            }
            then.accept(response);
        }, latency.toNanos(), TimeUnit.NANOSECONDS);
    }

    public void setTimeout(Runnable task, Duration delay) {
        clock.setTimeout(task, delay);
    }
}
