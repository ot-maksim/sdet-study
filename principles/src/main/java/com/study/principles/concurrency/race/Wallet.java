package com.study.principles.concurrency.race;

/**
 * The SUT's wallet API, as the test sees it over HTTP: {@code POST /accounts/{id}/withdrawals} answers 200, or
 * 409 when the balance is too low. Two implementations: {@link RacyWallet} (the bug to find) and {@link SafeWallet}.
 */
public interface Wallet {

    int OK = 200;
    int INSUFFICIENT_FUNDS = 409;

    void open(String accountId, long balance);

    /** 200 and the balance goes down, or 409 and nothing changes. */
    int withdraw(String accountId, long amount);

    void deposit(String accountId, long amount);

    long balance(String accountId);

    /** The most calls that were inside the wallet at once. */
    int maxInFlight();
}
