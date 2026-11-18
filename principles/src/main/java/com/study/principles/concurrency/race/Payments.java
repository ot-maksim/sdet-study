package com.study.principles.concurrency.race;

/**
 * {@code POST /charges} with an {@code Idempotency-Key} header: a client that retries after a timeout must not pay
 * twice. The retry often arrives while the first request is <b>still running</b>, so "same key, at the same time" is
 * the case that matters. Two implementations: {@link RacyPayments}, {@link IdempotentPayments}.
 */
public interface Payments {

    record Charge(String chargeId, String idempotencyKey, long amount) {
    }

    Charge charge(String idempotencyKey, long amount);

    /** How many times money was actually taken. */
    int chargesMade();
}
