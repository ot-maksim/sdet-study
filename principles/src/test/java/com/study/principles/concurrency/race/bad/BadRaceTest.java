package com.study.principles.concurrency.race.bad;

import com.study.principles.concurrency.race.RacyWallet;
import com.study.principles.concurrency.race.SafeWallet;
import com.study.principles.concurrency.race.Wallet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Characterization tests: they pass <i>because</i> they pin the problems of {@link ConcurrentCalls}.
 * The SUT is mostly the racy wallet: the question is whether the harness lets the test see the bug.
 */
class BadRaceTest {

    private static final long WINDOW_MS = 20;

    private ExecutorService pool;

    @AfterEach
    void shutdown() {
        pool.shutdownNow();
    }

    private Wallet racyWalletWith100() {
        Wallet wallet = new RacyWallet(WINDOW_MS);
        wallet.open("acc-1", 100);
        return wallet;
    }

    // --- 1. The pool decides the concurrency, not the test ------------------------------------------------

    @Test
    void sharedPoolOfOne_tenConcurrentWithdrawalsRunInSequence_theRacyWalletPasses() {
        pool = Executors.newSingleThreadExecutor();   // the framework's "async" executor
        Wallet wallet = racyWalletWith100();

        List<Integer> statuses = new ConcurrentCalls(pool).fire(10, () -> wallet.withdraw("acc-1", 100));

        assertThat(statuses).containsOnlyOnce(Wallet.OK);   // the assertion the test makes: green
        assertThat(wallet.balance("acc-1")).isZero();
        assertThat(wallet.maxInFlight()).as("calls that overlapped").isEqualTo(1);
    }

    @Test
    void sharedPoolOfTwo_theRaceShowsUpAsAtMostTwo_whateverTheSutDoes() {
        pool = Executors.newFixedThreadPool(2);
        Wallet wallet = racyWalletWith100();

        List<Integer> statuses = new ConcurrentCalls(pool).fire(10, () -> wallet.withdraw("acc-1", 100));

        assertThat(wallet.maxInFlight()).isEqualTo(2);
        assertThat(statuses).filteredOn(s -> s == Wallet.OK).hasSizeLessThanOrEqualTo(2);
    }

    // --- 2. Assertions inside the calls ---------------------------------------------------------------------

    @Test
    void perCallAssertions_everyCallLooksFine_theInvariantAcrossCallsIsNeverChecked() {
        pool = Executors.newFixedThreadPool(10);
        Wallet wallet = racyWalletWith100();

        assertThatCode(() -> new ConcurrentCalls(pool).fire(10, () -> {
            int status = wallet.withdraw("acc-1", 100);
            if (status != Wallet.OK && status != Wallet.INSUFFICIENT_FUNDS) {   // "then().statusCode(anyOf(200, 409))"
                throw new AssertionError("Unexpected status " + status);
            }
            return status;
        })).doesNotThrowAnyException();   // green

        assertThat(wallet.maxInFlight()).isEqualTo(10);   // and yet: 10 calls overlapped on a racy wallet...
        // ...each got a valid answer, most likely 200. "At most one 200" is about all calls together:
        // no single call can check it.
    }

    @Test
    void statusAssertionInsideTheCall_firstFailureThrows_andHidesWhatTheOtherCallsGot() {
        pool = Executors.newFixedThreadPool(10);
        Wallet wallet = new SafeWallet(WINDOW_MS);
        wallet.open("acc-1", 100);

        assertThatThrownBy(() -> new ConcurrentCalls(pool).fire(10, () -> {
            int status = wallet.withdraw("acc-1", 100);
            if (status != Wallet.OK) {
                throw new IllegalStateException(status + " Insufficient funds");   // ".then().statusCode(200)"
            }
            return status;
        })).isInstanceOf(CompletionException.class).hasMessageContaining("409");
        // Nine 409s are exactly right for a correct wallet; the test fails on the first one in join order and never
        // gets the list that would show "one 200, nine 409".
    }

    // --- 3. A barrier bigger than the pool -------------------------------------------------------------------

    @Test
    void cyclicBarrierForTenOnAPoolOfFour_waitsForever() {
        pool = Executors.newFixedThreadPool(4);
        Wallet wallet = racyWalletWith100();

        CompletableFuture<List<Integer>> test = CompletableFuture.supplyAsync(
                () -> new ConcurrentCalls(pool).fireTogether(10, () -> wallet.withdraw("acc-1", 100)));

        assertThatThrownBy(() -> test.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        assertThat(wallet.maxInFlight()).as("not one call reached the wallet").isZero();
        // shutdownNow() in @AfterEach interrupts the four parties stuck at the barrier.
    }
}
