package com.study.principles.concurrency.race;

import com.study.principles.concurrency.race.bad.ConcurrentCalls;
import com.study.principles.concurrency.race.good.StartingGate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Two axes: the harness (how the test fires 10 withdrawals of 100 from a balance of 100) and the SUT (racy or safe).
 * A harness is useful only if it tells the two wallets apart. Then the same for 10 charges with one idempotency key.
 *
 * <pre>
 * mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.concurrency.race.RaceDemo
 * </pre>
 */
public final class RaceDemo {

    private static final long WINDOW_MS = 20;
    private static final int CALLS = 10;

    public static void main(String[] args) {
        System.out.printf("%-34s %-22s %-22s%n", "10 x withdraw(100) from 100", "racy wallet", "safe wallet");
        sharedPool("bad: shared pool of 1", 1);
        sharedPool("bad: shared pool of 2", 2);
        row("good: starting gate, 10 threads",
                withdrawals(() -> new RacyWallet(WINDOW_MS), RaceDemo::gate),
                withdrawals(() -> new SafeWallet(WINDOW_MS), RaceDemo::gate));

        System.out.println();
        System.out.printf("%-34s %-22s %-22s%n", "10 x charge(\"key-1\")", "racy payments", "idempotent payments");
        row("good: starting gate, 10 threads",
                charges(new RacyPayments(WINDOW_MS)), charges(new IdempotentPayments(WINDOW_MS)));
    }

    private static void sharedPool(String name, int size) {
        ExecutorService pool = Executors.newFixedThreadPool(size);
        try {
            ConcurrentCalls calls = new ConcurrentCalls(pool);
            row(name,
                    withdrawals(() -> new RacyWallet(WINDOW_MS), w -> calls.fire(CALLS, () -> w.withdraw("acc", 100))),
                    withdrawals(() -> new SafeWallet(WINDOW_MS), w -> calls.fire(CALLS, () -> w.withdraw("acc", 100))));
        } finally {
            pool.shutdownNow();
        }
    }

    private static List<Integer> gate(Wallet wallet) {
        return StartingGate.runTogether(CALLS, party -> wallet.withdraw("acc", 100), Duration.ofSeconds(5))
                .stream().map(StartingGate.Outcome::value).toList();
    }

    private static String withdrawals(Supplier<Wallet> walletFactory, java.util.function.Function<Wallet, List<Integer>> fire) {
        Wallet wallet = walletFactory.get();
        wallet.open("acc", 100);
        long paid = fire.apply(wallet).stream().filter(s -> s == Wallet.OK).count();
        return String.format("%2d paid, overlap %2d", paid, wallet.maxInFlight());
    }

    private static String charges(Payments payments) {
        StartingGate.runTogether(CALLS, party -> payments.charge("key-1", 4200), Duration.ofSeconds(5));
        return String.format("%2d charges made", payments.chargesMade());
    }

    private static void row(String name, String racy, String safe) {
        System.out.printf("%-34s %-22s %-22s%n", name, racy, safe);
    }
}
