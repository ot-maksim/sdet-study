package com.study.principles.parallel.good;

import com.study.principles.parallel.Credentials;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Exclusive leases of a scarce resource that cannot be created per test (pre-provisioned premium
 * accounts, a device in a farm, a phone number for SMS). Shared by the whole run, but each item is
 * used by <b>one test at a time</b>.
 *
 * <p>A {@link BlockingQueue} is the whole implementation: {@code poll(timeout)} waits for a free item,
 * {@code offer} gives it back. Fair ordering, so a test cannot starve. The timeout turns "a test forgot to
 * release" from a hung build into a clear failure.
 */
public final class AccountPool {

    private final BlockingQueue<Credentials> free;
    private final int size;
    private final Duration timeout;

    public AccountPool(Collection<Credentials> accounts, Duration timeout) {
        this.size = accounts.size();
        this.free = new ArrayBlockingQueue<>(Math.max(1, size), true, accounts);
        this.timeout = timeout;
    }

    public Credentials lease() {
        try {
            Credentials account = free.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (account == null) {
                throw new IllegalStateException("no free account within " + timeout + ", all " + size
                        + " are leased: a test does not release, or the pool is too small for this parallelism");
            }
            return account;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for an account", e);
        }
    }

    public void release(Credentials account) {
        if (!free.offer(account)) {
            throw new IllegalStateException("released more accounts than leased: " + account);
        }
    }

    public int size() {
        return size;
    }

    public int available() {
        return free.size();
    }
}
