package com.study.principles.concurrency.race.good;

import com.study.principles.concurrency.race.IdempotentPayments;
import com.study.principles.concurrency.race.Payments;
import com.study.principles.concurrency.race.Payments.Charge;
import com.study.principles.concurrency.race.RacyPayments;
import com.study.principles.concurrency.race.RacyWallet;
import com.study.principles.concurrency.race.SafeWallet;
import com.study.principles.concurrency.race.Wallet;
import com.study.principles.concurrency.race.good.StartingGate.Outcome;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The starting gate against both versions of the SUT: it must <b>find</b> the bug in the racy one and <b>pass</b> on
 * the safe one. A harness that passes on both proves nothing.
 */
class GoodRaceTest {

    private static final long WINDOW_MS = 20;
    private static final int PARTIES = 10;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static List<Integer> withdrawTogether(Wallet wallet, String account, long amount) {
        List<Outcome<Integer>> outcomes = StartingGate.runTogether(PARTIES, party -> wallet.withdraw(account, amount), TIMEOUT);
        assertThat(outcomes).noneMatch(Outcome::failed);
        return outcomes.stream().map(Outcome::value).toList();
    }

    // --- 1. The calls really overlap ---------------------------------------------------------------------------

    @Test
    void allPartiesAreInsideTheSutAtOnce() {
        Wallet wallet = new SafeWallet(WINDOW_MS);
        wallet.open("acc-1", 100);

        withdrawTogether(wallet, "acc-1", 100);

        assertThat(wallet.maxInFlight()).isEqualTo(PARTIES);
    }

    // --- 2. Double spend ---------------------------------------------------------------------------------------

    @Test
    void racyWallet_doubleSpendIsFound() {
        Wallet wallet = new RacyWallet(WINDOW_MS);
        wallet.open("acc-1", 100);

        List<Integer> statuses = withdrawTogether(wallet, "acc-1", 100);

        long paid = statuses.stream().filter(s -> s == Wallet.OK).count();
        assertThat(paid).as("withdrawals of 100 paid from a balance of 100").isGreaterThan(1);
        assertThat(wallet.balance("acc-1")).as("and the balance looks fine").isZero();
    }

    @Test
    void safeWallet_exactlyOneWithdrawalSucceeds_roundAfterRound() {
        Wallet wallet = new SafeWallet(WINDOW_MS);
        for (int round = 0; round < 20; round++) {   // a race test is evidence, not proof: repeat it
            String account = "acc-" + round;
            wallet.open(account, 100);

            List<Integer> statuses = withdrawTogether(wallet, account, 100);

            assertThat(statuses).as("round %d", round).containsOnlyOnce(Wallet.OK)
                    .filteredOn(s -> s != Wallet.OK).containsOnly(Wallet.INSUFFICIENT_FUNDS);
            assertThat(wallet.balance(account)).isZero();
        }
    }

    // --- 3. Lost update ------------------------------------------------------------------------------------------

    @Test
    void racyWallet_lostUpdateOnConcurrentDeposits() {
        Wallet wallet = new RacyWallet(WINDOW_MS);
        wallet.open("acc-1", 0);

        StartingGate.runTogether(PARTIES, party -> {
            wallet.deposit("acc-1", 10);
            return null;
        }, TIMEOUT);

        assertThat(wallet.balance("acc-1")).as("10 deposits of 10").isLessThan(100);
    }

    @Test
    void safeWallet_everyDepositCounts() {
        Wallet wallet = new SafeWallet(WINDOW_MS);
        wallet.open("acc-1", 0);

        StartingGate.runTogether(PARTIES, party -> {
            wallet.deposit("acc-1", 10);
            return null;
        }, TIMEOUT);

        assertThat(wallet.balance("acc-1")).isEqualTo(100);
    }

    // --- 4. Idempotency key --------------------------------------------------------------------------------------

    @Test
    void racyPayments_sameKeyAtTheSameTime_chargesSeveralTimes() {
        Payments payments = new RacyPayments(WINDOW_MS);

        List<Outcome<Charge>> outcomes = StartingGate.runTogether(PARTIES, party -> payments.charge("key-1", 4200), TIMEOUT);

        assertThat(payments.chargesMade()).as("retries of one payment").isGreaterThan(1);
        assertThat(outcomes).extracting(o -> o.value().chargeId()).doesNotHaveDuplicates().hasSizeGreaterThan(1);
    }

    @Test
    void idempotentPayments_sameKeyAtTheSameTime_chargesOnce_andEveryoneGetsThatCharge() {
        Payments payments = new IdempotentPayments(WINDOW_MS);

        List<Outcome<Charge>> outcomes = StartingGate.runTogether(PARTIES, party -> payments.charge("key-1", 4200), TIMEOUT);

        assertThat(payments.chargesMade()).isEqualTo(1);
        assertThat(outcomes).extracting(o -> o.value().chargeId()).containsOnly("ch_1");
    }

    @Test
    void idempotentPayments_differentKeys_areNotMergedByMistake() {
        Payments payments = new IdempotentPayments(WINDOW_MS);

        StartingGate.runTogether(PARTIES, party -> payments.charge("key-" + party, 4200), TIMEOUT);

        assertThat(payments.chargesMade()).isEqualTo(PARTIES);   // over-deduplicating is a bug too
    }

    // --- 5. The harness itself ------------------------------------------------------------------------------------

    @Test
    void oneFailingCall_doesNotHideTheOthers() {
        List<Outcome<String>> outcomes = StartingGate.runTogether(PARTIES, party -> {
            if (party == 3) {
                throw new IllegalStateException("409 Insufficient funds");
            }
            return "ok";
        }, TIMEOUT);

        assertThat(outcomes).filteredOn(Outcome::failed).singleElement()
                .satisfies(o -> assertThat(o.party()).isEqualTo(3))
                .satisfies(o -> assertThat(o.error()).hasMessage("409 Insufficient funds"));
        assertThat(outcomes).filteredOn(o -> !o.failed()).hasSize(PARTIES - 1);
    }

    @Test
    void aHungCall_isReportedByNumber_andTheTestDoesNotHang() {
        CountDownLatch never = new CountDownLatch(1);

        List<Outcome<String>> outcomes = StartingGate.runTogether(3, party -> {
            if (party == 0) {
                try {
                    never.await();   // a request without a client timeout against a stuck server
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return "ok";
        }, Duration.ofMillis(300));

        assertThat(outcomes.get(0).error()).isInstanceOf(TimeoutException.class)
                .hasMessage("Party 0 did not finish within PT0.3S");
        assertThat(outcomes.subList(1, 3)).extracting(Outcome::value).containsOnly("ok");
    }
}
