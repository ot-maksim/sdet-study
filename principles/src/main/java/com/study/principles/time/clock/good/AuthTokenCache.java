package com.study.principles.time.clock.good;

import com.study.principles.time.clock.AccessToken;
import com.study.principles.time.clock.TokenIssuer;

import java.time.Clock;
import java.time.Duration;

/**
 * The same token cache with time injected as a dependency.
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li><b>{@link Clock} is a constructor dependency</b>, like the issuer. The composition root passes
 *       {@code Clock.systemUTC()}; a test passes a {@code MutableClock} and moves it to 1 ms before
 *       and exactly at the refresh boundary, without sleeping.</li>
 *   <li><b>No default constructor with {@code Clock.systemUTC()}.</b> A convenience overload makes it
 *       too easy to forget the clock in one place and get a test that passes for the wrong reason.
 *       (A package-private test-only overload would be a reasonable compromise in a big codebase.)</li>
 *   <li><b>Only the instant is read</b> ({@code clock.instant()}), so the clock's zone is irrelevant here.</li>
 * </ul>
 */
public final class AuthTokenCache {
    public static final Duration REFRESH_MARGIN = Duration.ofSeconds(30);

    private final TokenIssuer issuer;
    private final Clock clock;
    private AccessToken current;

    public AuthTokenCache(TokenIssuer issuer, Clock clock) {
        this.issuer = issuer;
        this.clock = clock;
    }

    public synchronized String bearer() {
        if (current == null || !clock.instant().isBefore(current.expiresAt().minus(REFRESH_MARGIN))) {
            current = issuer.issue();
        }
        return current.value();
    }
}
