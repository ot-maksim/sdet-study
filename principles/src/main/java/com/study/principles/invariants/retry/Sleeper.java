package com.study.principles.invariants.retry;

import java.time.Duration;

/**
 * Pause between attempts. Injected so that tests and demos record the delays instead of really
 * sleeping (production would pass {@code d -> Thread.sleep(d.toMillis())}).
 */
@FunctionalInterface
public interface Sleeper {

    void sleep(Duration delay);
}
