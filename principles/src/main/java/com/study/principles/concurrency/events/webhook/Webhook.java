package com.study.principles.concurrency.events.webhook;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One delivery as the receiver saw it. A retry of the same event is another {@code Webhook} with the same
 * {@link #eventId()} and a higher {@link #attempt()}: deduplicate by the id, never by the body.
 */
public record Webhook(String eventId, int attempt, String body, String signature, Instant receivedAt) {

    public Webhook {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(receivedAt, "receivedAt");
    }

    /** The value of a flat string field of the JSON body, or {@code null}. */
    public String field(String name) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
        return m.find() ? m.group(1) : null;
    }

    public boolean hasValidSignature(String secret) {
        return Signatures.verify(secret, body, signature);
    }
}
