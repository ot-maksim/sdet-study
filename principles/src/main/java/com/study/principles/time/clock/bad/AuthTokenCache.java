package com.study.principles.time.clock.bad;

import com.study.principles.time.clock.AccessToken;
import com.study.principles.time.clock.TokenIssuer;

import java.time.Duration;
import java.time.Instant;

/**
 * Framework-side token cache: reuses the token and asks for a new one shortly before it expires.
 *
 * <h2>What is wrong</h2>
 * <ul>
 *   <li><b>Hidden dependency on the system clock</b> ({@code Instant.now()}). The refresh branch is
 *       the whole point of the class, yet a test can reach it only by really waiting until the
 *       token is about to expire: a 15-minute token means a 15-minute test, so people shrink the
 *       TTL and add {@code Thread.sleep}, and the suite gets slow and timing-sensitive.</li>
 *   <li><b>Boundaries are untestable.</b> "Refresh exactly 30 s before expiry" cannot be checked
 *       at 29.999 s vs 30.000 s against a clock that keeps moving.</li>
 * </ul>
 */
public final class AuthTokenCache {
    public static final Duration REFRESH_MARGIN = Duration.ofSeconds(30);

    private final TokenIssuer issuer;
    private AccessToken current;

    public AuthTokenCache(TokenIssuer issuer) {
        this.issuer = issuer;
    }

    public synchronized String bearer() {
        if (current == null || !Instant.now().isBefore(current.expiresAt().minus(REFRESH_MARGIN))) {
            current = issuer.issue();
        }
        return current.value();
    }
}
