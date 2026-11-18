package com.study.principles.concurrency.fanout;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The test-data API of the SUT: {@code POST /users}, {@code /products}, {@code /stock}, {@code /promos}, each slow
 * (a real stand answers in hundreds of milliseconds) and independent of the others. Entities live until deleted;
 * {@link #live()} shows what a test left behind.
 *
 * <p>With {@code requireAuth}, every call needs the token from {@link TestContext}, read on the calling thread, as a
 * client with a per-test auth filter would. {@link #failOn} and {@link #hangOn} break one type of call.
 */
public final class FakeShopApi {

    public record Entity(String type, String id) {
    }

    public static final class Unauthorized extends RuntimeException {
        public Unauthorized(String type) {
            super("401 Unauthorized: POST /" + type + "s without a token");
        }
    }

    private final Duration latency;
    private final boolean requireAuth;
    private final Set<Entity> live = ConcurrentHashMap.newKeySet();
    private final Map<String, RuntimeException> failures = new ConcurrentHashMap<>();
    private final Map<String, Duration> hangs = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger maxInFlight = new AtomicInteger();

    public FakeShopApi(Duration latency, boolean requireAuth) {
        this.latency = latency;
        this.requireAuth = requireAuth;
    }

    /** Creates an entity of the given type and returns its id. */
    public String create(String type) {
        if (requireAuth && TestContext.token() == null) {
            throw new Unauthorized(type);
        }
        maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
        try {
            sleep(hangs.getOrDefault(type, latency));
            RuntimeException failure = failures.get(type);
            if (failure != null) {
                throw failure;
            }
            String id = type + "-" + ids.incrementAndGet();
            live.add(new Entity(type, id));
            return id;
        } finally {
            inFlight.decrementAndGet();
        }
    }

    public void delete(String type, String id) {
        live.remove(new Entity(type, id));
    }

    public Set<Entity> live() {
        return Set.copyOf(live);
    }

    public int maxInFlight() {
        return maxInFlight.get();
    }

    public void failOn(String type, RuntimeException failure) {
        failures.put(type, failure);
    }

    /** Calls for this type take {@code duration} instead of the normal latency: a stuck dependency. */
    public void hangOn(String type, Duration duration) {
        hangs.put(type, duration);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted: request cancelled", e);
        }
    }
}
