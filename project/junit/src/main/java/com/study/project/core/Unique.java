package com.study.project.core;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collision-free test data for a shared stand: a short run id (different for every run, so reruns and
 * parallel CI jobs do not collide on unique columns) plus a counter (different for every call, thread-safe).
 * Readable on purpose: {@code ada-k3x9q-7@example.test} tells which run created the row.
 *
 * <p>Run-scoped and injected, not static: a test that needs a fixed id can get its own instance.
 */
public final class Unique {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final String runId;
    private final AtomicLong counter = new AtomicLong();

    public Unique() {
        this(randomRunId());
    }

    public Unique(String runId) {
        if (!runId.matches("[a-z0-9]{1,12}")) {
            throw new IllegalArgumentException("run id must be 1..12 of [a-z0-9]: " + runId);
        }
        this.runId = runId;
    }

    public String runId() {
        return runId;
    }

    /** {@code k3x9q-7} */
    public String suffix() {
        return runId + "-" + counter.incrementAndGet();
    }

    /** {@code ada-k3x9q-7@example.test}: {@code .test} is a reserved TLD, nothing is ever delivered. */
    public String email(String who) {
        return who + "-" + suffix() + "@example.test";
    }

    /** {@code Ada k3x9q-8} */
    public String name(String base) {
        return base + " " + suffix();
    }

    private static String randomRunId() {
        SecureRandom random = new SecureRandom();
        StringBuilder id = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            id.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return id.toString();
    }
}
