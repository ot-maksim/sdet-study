package com.study.principles.time.clock;

import java.time.Clock;
import java.time.Duration;

/**
 * Fake auth server: issues {@code token-1}, {@code token-2}, ... valid for {@code ttl} from "now"
 * by <i>its own</i> clock, and counts how many times it was called.
 */
public final class FakeTokenIssuer implements TokenIssuer {
    private final Clock serverClock;
    private final Duration ttl;
    private int issued;

    public FakeTokenIssuer(Clock serverClock, Duration ttl) {
        this.serverClock = serverClock;
        this.ttl = ttl;
    }

    @Override
    public synchronized AccessToken issue() {
        issued++;
        return new AccessToken("token-" + issued, serverClock.instant().plus(ttl));
    }

    public synchronized int issued() {
        return issued;
    }
}
