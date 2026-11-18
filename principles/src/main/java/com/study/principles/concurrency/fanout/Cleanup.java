package com.study.principles.concurrency.fanout;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Undo actions for test data, run in reverse order when the test ends (an extension's {@code afterEach}).
 *
 * <ul>
 *   <li><b>Register right after each creation</b>, from whatever thread created it: a test that fails half-way
 *       still deletes what it made.</li>
 *   <li><b>A late registration runs at once.</b> A branch that was still running when the test gave up finishes later;
 *       if the cleanup has already run, its entity would leak. Here it is deleted the moment it registers.</li>
 *   <li>One failing undo does not stop the others; all failures are reported together.</li>
 * </ul>
 */
public final class Cleanup implements AutoCloseable {

    private final Deque<Runnable> actions = new ArrayDeque<>();   // guarded by this
    private boolean closed;                                       // guarded by this

    public void register(Runnable undo) {
        synchronized (this) {
            if (!closed) {
                actions.push(undo);
                return;
            }
        }
        undo.run();   // too late for the batch: undo now
    }

    @Override
    public void close() {
        Deque<Runnable> toRun;
        synchronized (this) {
            closed = true;
            toRun = new ArrayDeque<>(actions);
            actions.clear();
        }
        RuntimeException failures = null;
        for (Runnable undo : toRun) {
            try {
                undo.run();
            } catch (RuntimeException e) {
                if (failures == null) {
                    failures = new IllegalStateException("Cleanup failed");
                }
                failures.addSuppressed(e);
            }
        }
        if (failures != null) {
            throw failures;
        }
    }
}
