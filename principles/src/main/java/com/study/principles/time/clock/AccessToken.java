package com.study.principles.time.clock;

import java.time.Instant;
import java.util.Objects;

/**
 * Bearer token as returned by the auth server: an opaque value and the moment it stops working.
 *
 * <p>{@code expiresAt} is an {@link Instant}: expiry is a point on the timeline, the same for
 * everybody, so no zone and no local date-time belongs here.
 */
public record AccessToken(String value, Instant expiresAt) {

    public AccessToken {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    @Override
    public String toString() {
        return "AccessToken[value=***, expiresAt=" + expiresAt + "]";
    }
}
