package com.study.principles.concurrency.race;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The fix: the check and the write are one atomic step, as a conditional update in SQL.
 *
 * <pre>
 * UPDATE accounts SET balance = balance - ? WHERE id = ? AND balance >= ?   -- 1 row: 200, 0 rows: 409
 * </pre>
 *
 * The slow part (the window) is still there and still concurrent; only the read-modify-write is not.
 */
public final class SafeWallet implements Wallet {

    private final Map<String, Long> balances = new ConcurrentHashMap<>();
    private final InFlight inFlight = new InFlight();
    private final long windowMillis;

    public SafeWallet(long windowMillis) {
        this.windowMillis = windowMillis;
    }

    @Override
    public void open(String accountId, long balance) {
        balances.put(accountId, balance);
    }

    @Override
    public int withdraw(String accountId, long amount) {
        inFlight.enter();
        try {
            InFlight.pause(windowMillis);
            AtomicBoolean done = new AtomicBoolean();
            balances.computeIfPresent(accountId, (id, balance) -> {
                if (balance < amount) {
                    return balance;
                }
                done.set(true);
                return balance - amount;
            });
            return done.get() ? OK : INSUFFICIENT_FUNDS;
        } finally {
            inFlight.exit();
        }
    }

    @Override
    public void deposit(String accountId, long amount) {
        inFlight.enter();
        try {
            InFlight.pause(windowMillis);
            balances.merge(accountId, amount, Long::sum);   // UPDATE ... SET balance = balance + ?
        } finally {
            inFlight.exit();
        }
    }

    @Override
    public long balance(String accountId) {
        return balances.get(accountId);
    }

    @Override
    public int maxInFlight() {
        return inFlight.max();
    }
}
