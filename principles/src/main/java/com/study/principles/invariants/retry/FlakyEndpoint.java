package com.study.principles.invariants.retry;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Fake HTTP endpoint: answers with the scripted status codes in order, then {@code 200} forever.
 * Counts calls so a test can see how many requests were actually sent.
 */
public final class FlakyEndpoint {
    private final Deque<Integer> script;
    private int calls;

    public FlakyEndpoint(Integer... statuses) {
        this.script = new ArrayDeque<>(List.of(statuses));
    }

    /** One "GET": returns the next scripted status. */
    public int get() {
        calls++;
        return script.isEmpty() ? 200 : script.poll();
    }

    public int calls() {
        return calls;
    }
}
