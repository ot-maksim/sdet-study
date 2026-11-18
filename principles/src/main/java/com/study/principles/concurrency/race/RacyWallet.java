package com.study.principles.concurrency.race;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The bug the race tests are for: read, think, write.
 *
 * <pre>
 * SELECT balance FROM accounts WHERE id = ?          -- 100
 * ... check the limit, call the fraud service ...     -- the window
 * UPDATE accounts SET balance = ? WHERE id = ?       -- 100 - amount, computed from a stale read
 * </pre>
 *
 * Every single statement is atomic (the map is concurrent), the sequence is not. Two requests inside the window
 * both see 100: a <b>double spend</b> on withdrawals, a <b>lost update</b> on deposits. Sequential tests never see it.
 */
public final class RacyWallet implements Wallet {

    private final Map<String, Long> balances = new ConcurrentHashMap<>();
    private final InFlight inFlight = new InFlight();
    private final long windowMillis;

    public RacyWallet(long windowMillis) {
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
            long balance = balances.get(accountId);
            InFlight.pause(windowMillis);
            if (balance < amount) {
                return INSUFFICIENT_FUNDS;
            }
            balances.put(accountId, balance - amount);
            return OK;
        } finally {
            inFlight.exit();
        }
    }

    @Override
    public void deposit(String accountId, long amount) {
        inFlight.enter();
        try {
            long balance = balances.get(accountId);
            InFlight.pause(windowMillis);
            balances.put(accountId, balance + amount);
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
