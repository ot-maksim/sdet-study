package com.study.principles.concurrency.race;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Idempotency as usually written first: "seen this key? return the stored charge; otherwise charge and store".
 * Correct for a retry that comes after the first request finished; wrong for one that comes during it:
 * both see "not seen", both charge.
 */
public final class RacyPayments implements Payments {

    private final Map<String, Charge> byKey = new ConcurrentHashMap<>();
    private final AtomicInteger charges = new AtomicInteger();
    private final long windowMillis;

    public RacyPayments(long windowMillis) {
        this.windowMillis = windowMillis;
    }

    @Override
    public Charge charge(String idempotencyKey, long amount) {
        Charge seen = byKey.get(idempotencyKey);
        if (seen != null) {
            return seen;
        }
        InFlight.pause(windowMillis);   // the call to the card network
        Charge charge = new Charge("ch_" + charges.incrementAndGet(), idempotencyKey, amount);
        byKey.put(idempotencyKey, charge);
        return charge;
    }

    @Override
    public int chargesMade() {
        return charges.get();
    }
}
