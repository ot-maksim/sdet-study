package com.study.framework.core.lifecycle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Owns what was created during a test (or a run) and undoes it at the end, in reverse order: the page before
 * the browser, the order before the user it belongs to. Every action runs even if an earlier one failed;
 * failures are collected into one {@link CleanupFailedException}, so a leak is reported instead of silently
 * left in the database.
 *
 * <p>Thread-safe: steps running in parallel branches of one test may register. Registration after
 * {@link #close()} is refused — that action would never run (see principles/concurrency, fanout).
 */
public final class Scope implements Cleanup, AutoCloseable {

    private record Entry(String description, Action action) {
    }

    private final String name;
    private final Deque<Entry> entries = new ArrayDeque<>();
    private boolean closed;

    public Scope(String name) {
        this.name = name;
    }

    @Override
    public synchronized void defer(String description, Action action) {
        if (closed) {
            throw new IllegalStateException("Scope '" + name + "' is already closed; '" + description + "' would never run");
        }
        entries.push(new Entry(description, action));
    }

    @Override
    public void close() {
        List<Entry> toRun;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            toRun = new ArrayList<>(entries);   // push() made it last-in-first
            entries.clear();
        }
        List<Exception> failures = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (Entry entry : toRun) {
            try {
                entry.action().run();
            } catch (Exception e) {
                failures.add(e);
                failed.add(entry.description() + ": " + e);
            }
        }
        if (!failures.isEmpty()) {
            CleanupFailedException error = new CleanupFailedException(failures.size() + " of " + toRun.size()
                    + " cleanup actions failed in '" + name + "':\n  - " + String.join("\n  - ", failed));
            failures.forEach(error::addSuppressed);
            throw error;
        }
    }

    @Override
    public String toString() {
        return "Scope(" + name + ")";
    }
}
