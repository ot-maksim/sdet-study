package com.study.principles.concurrency.fanout;

import java.util.function.Supplier;

/**
 * Per-test state kept in a {@code ThreadLocal}, as frameworks do for the current test's auth token, its id in the
 * logs (SLF4J {@code MDC} works the same way), or the report's step stack. Set by an extension on the test thread.
 *
 * <p>A {@code ThreadLocal} does not travel: code running on a pool thread sees that thread's value (usually none, or
 * worse, a stale one from the last task the thread ran). {@link #wrap} carries it over explicitly.
 */
public final class TestContext {

    private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

    private TestContext() {
    }

    public static void setToken(String token) {
        TOKEN.set(token);
    }

    public static String token() {
        return TOKEN.get();
    }

    public static void clear() {
        TOKEN.remove();
    }

    /**
     * Captures the caller's context now, sets it on the worker thread for the duration of the task, and restores the
     * worker's previous value afterwards: pool threads are reused, so a leftover token would leak into the next task.
     */
    public static <T> Supplier<T> wrap(Supplier<T> task) {
        String captured = TOKEN.get();
        return () -> {
            String previous = TOKEN.get();
            TOKEN.set(captured);
            try {
                return task.get();
            } finally {
                if (previous == null) {
                    TOKEN.remove();
                } else {
                    TOKEN.set(previous);
                }
            }
        };
    }
}
