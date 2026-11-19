package com.study.review.good.data;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Undo actions of one test, registered at the moment something is created and run in reverse order when the
 * test ends — passed or failed. Removes exactly what this test made: no "delete everything with my prefix" that
 * also removes a parallel test's data. Every action runs; failures are collected. Full version:
 * {@code framework/core} {@code Scope}.
 */
public final class Cleanup implements AutoCloseable {

    private record Action(String what, Runnable undo) {
    }

    private final Deque<Action> actions = new ArrayDeque<>();

    public synchronized void register(String what, Runnable undo) {
        actions.push(new Action(what, undo));
    }

    @Override
    public synchronized void close() {
        IllegalStateException failure = null;
        while (!actions.isEmpty()) {
            Action action = actions.pop();
            try {
                action.undo().run();
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = new IllegalStateException("Cleanup failed");
                }
                failure.addSuppressed(new IllegalStateException(action.what() + ": " + e.getMessage(), e));
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
