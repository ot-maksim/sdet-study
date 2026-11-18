package com.study.principles.concurrency.eventual.bad;

import com.study.principles.concurrency.eventual.FakeOrderSystem;
import com.study.principles.concurrency.eventual.OrderView.Status;
import com.study.principles.concurrency.eventual.ReadErrors;
import com.study.principles.concurrency.eventual.ThreadBoundClient;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/** Characterization tests: they pass <i>because</i> they pin the ways a wait for eventual consistency goes wrong. */
class BadEventualTest {

    private FakeOrderSystem system;

    @AfterEach
    void close() {
        system.close();
    }

    // --- 1. Not waiting, or waiting a fixed time ------------------------------------------------------------

    @Test
    void assertRightAfterTheWrite_theReadSideHasNotCaughtUp() {
        system = new FakeOrderSystem(Duration.ofMillis(200), Duration.ZERO);

        String id = system.placeOrder("alice");   // 201 Created...

        assertThatThrownBy(() -> system.getOrder(id)).isInstanceOf(ReadErrors.NotFound.class);   // ...404 on read
    }

    @Test
    void fixedSleepShorterThanThisRunsLag_stillNotThere() throws InterruptedException {
        system = new FakeOrderSystem(Duration.ofMillis(300), Duration.ZERO);

        String id = system.placeOrder("alice");
        Thread.sleep(100);   // enough on the laptop; a longer one costs its full length on every run

        assertThatThrownBy(() -> system.getOrder(id)).isInstanceOf(ReadErrors.NotFound.class);
    }

    // --- 2. A hand-rolled loop -------------------------------------------------------------------------------

    @Test
    void noDeadlineAndCatchAll_a401SpinsUntilSomebodyKillsTheThread() throws InterruptedException {
        system = new FakeOrderSystem(Duration.ofMillis(50), Duration.ZERO);
        String id = system.placeOrder("alice");
        system.breakReads(new ReadErrors.Unauthorized());   // the token expired
        OrderChecks checks = new OrderChecks(system);

        Thread test = new Thread(() -> {
            try {
                checks.waitForOrder(id);
            } catch (InterruptedException e) {
                // the CI job's timeout
            }
        });
        test.start();
        test.join(1000);

        assertThat(test.isAlive()).as("still waiting after 1 s").isTrue();
        assertThat(system.reads()).as("401s swallowed as 'not yet'").isGreaterThan(10);
        test.interrupt();
        test.join();
    }

    @Test
    void waitingForAnExactIntermediateState_failsWhenTheReadSideJumpsPastIt() throws InterruptedException {
        system = new FakeOrderSystem(Duration.ofMillis(50), Duration.ZERO);   // ships the moment it is paid
        String id = system.placeOrder("alice");
        new com.study.principles.concurrency.eventual.good.OrderChecks(system, Duration.ofSeconds(5)).orderVisible(id);

        system.pay(id);

        assertThat(new OrderChecks(system).waitForStatus(id, Status.PAID, Duration.ofSeconds(1))).isFalse();
        assertThat(system.getOrder(id).status()).isEqualTo(Status.SHIPPED);   // everything went right
    }

    // --- 3. Awaitility used carelessly -------------------------------------------------------------------------

    @Test
    void awaitilityByDefault_evaluatesOnItsOwnThread_aThreadBoundClientBreaks() {
        system = new FakeOrderSystem(Duration.ofMillis(50), Duration.ZERO);
        String id = system.placeOrder("alice");
        ThreadBoundClient client = new ThreadBoundClient(system);   // a Playwright page, a ThreadLocal-scoped client

        assertThatThrownBy(() -> await().atMost(Duration.ofSeconds(2))
                .ignoreException(ReadErrors.NotFound.class)
                .until(() -> client.getOrder(id) != null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageEndingWith("used from 'awaitility-thread'");
    }

    @Test
    void awaitilityIgnoringAllExceptions_turnsA401IntoATimeout() {
        system = new FakeOrderSystem(Duration.ofMillis(50), Duration.ZERO);
        String id = system.placeOrder("alice");
        system.breakReads(new ReadErrors.Unauthorized());

        assertThatThrownBy(() -> await().atMost(Duration.ofMillis(500))
                .ignoreExceptions()                       // "404s are expected"; so are 401s now
                .until(() -> system.getOrder(id) != null))
                .isInstanceOf(ConditionTimeoutException.class)
                .hasMessageNotContaining("401");
    }
}
