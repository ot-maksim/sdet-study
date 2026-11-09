package com.study.principles.time.clock;

import com.study.principles.time.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TokenCacheTest {
    private static final Duration TTL = Duration.ofMinutes(15);

    /**
     * The only way to reach the refresh branch of the bad cache: shrink the TTL to "margin + a bit"
     * and really sleep. The test knows the private margin, wastes real time, and on a loaded CI
     * agent the first two calls may already be past the boundary.
     */
    @Test
    void badCacheRefreshCanOnlyBeReachedBySleeping() throws InterruptedException {
        var issuer = new FakeTokenIssuer(Clock.systemUTC(),
                com.study.principles.time.clock.bad.AuthTokenCache.REFRESH_MARGIN.plusMillis(200));
        var cache = new com.study.principles.time.clock.bad.AuthTokenCache(issuer);

        assertThat(cache.bearer()).isEqualTo("token-1");
        Thread.sleep(300);

        assertThat(cache.bearer()).isEqualTo("token-2");
    }

    @Test
    void goodCacheReusesTheTokenUntilTheRefreshBoundary() {
        var clock = new MutableClock(Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC);
        var issuer = new FakeTokenIssuer(clock, TTL);
        var cache = new com.study.principles.time.clock.good.AuthTokenCache(issuer, clock);

        assertThat(cache.bearer()).isEqualTo("token-1");
        clock.advance(TTL.minus(com.study.principles.time.clock.good.AuthTokenCache.REFRESH_MARGIN).minusMillis(1));

        assertThat(cache.bearer()).isEqualTo("token-1");
        assertThat(issuer.issued()).isOne();
    }

    @Test
    void goodCacheRefreshesExactlyAtTheBoundary() {
        var clock = new MutableClock(Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC);
        var issuer = new FakeTokenIssuer(clock, TTL);
        var cache = new com.study.principles.time.clock.good.AuthTokenCache(issuer, clock);

        cache.bearer();
        clock.advance(TTL.minus(com.study.principles.time.clock.good.AuthTokenCache.REFRESH_MARGIN));

        assertThat(cache.bearer()).isEqualTo("token-2");
    }
}
