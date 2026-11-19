package com.study.review.good.data;

import com.study.review.good.api.NewUser;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Unique test data for a shared stand: a run id (reruns and parallel CI jobs differ) plus a counter (every call
 * differs, thread-safe). Run-scoped and injected, not static. Full version: {@code framework/core} {@code Unique}.
 */
public final class TestUsers {

    private final String runId;
    private final AtomicLong counter = new AtomicLong();

    public TestUsers(String runId) {
        if (!runId.matches("[a-z0-9]{1,12}")) {
            throw new IllegalArgumentException("run id must be 1..12 of [a-z0-9]: " + runId);
        }
        this.runId = runId;
    }

    public static TestUsers forNewRun() {
        String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder id = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            id.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return new TestUsers(id.toString());
    }

    /** {@code ada-k3x9q-7@example.test}: tells which run created the row; {@code .test} never delivers mail. */
    public String email(String who) {
        return who + "-" + runId + "-" + counter.incrementAndGet() + "@example.test";
    }

    /** A valid user; a test overrides only the fields its story is about. */
    public NewUser aUser() {
        return new NewUser("Ada Lovelace", email("ada"), 36, "GB");
    }
}
