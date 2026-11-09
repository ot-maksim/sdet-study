package com.study.principles.time.assertions;

/**
 * Response of {@code POST /orders} as the test receives it: timestamps are plain JSON strings,
 * exactly as the server serialized them.
 */
public record OrderResponse(String id, String createdAt, String expiresAt) {
}
