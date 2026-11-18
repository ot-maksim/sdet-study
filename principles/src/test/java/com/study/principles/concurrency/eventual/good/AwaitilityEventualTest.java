package com.study.principles.concurrency.eventual.good;

import com.study.principles.concurrency.eventual.FakeOrderSystem;
import com.study.principles.concurrency.eventual.OrderView;
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

/**
 * The same waits with Awaitility: what the library adds over {@link Poll}, and the two settings to get right
 * (which exceptions mean "not yet", which thread runs the condition).
 */
class AwaitilityEventualTest {

    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final Duration POLL = Duration.ofMillis(50);

    private FakeOrderSystem system;

    @AfterEach
    void close() {
        system.close();
    }

    @Test
    void untilAsserted_withAnAlias_andOnly404IgnoredAsNotYet() {
        system = new FakeOrderSystem(Duration.ofMillis(100), Duration.ZERO);
        String id = system.placeOrder("alice");
        system.pay(id);

        await(id + " paid in the read model").atMost(WAIT).pollInterval(POLL)
                .ignoreException(ReadErrors.NotFound.class)
                .untilAsserted(() -> assertThat(system.getOrder(id).status().isAtLeast(Status.PAID)).isTrue());
    }

    @Test
    void untilWithASupplier_returnsTheValueThatMatched() {
        system = new FakeOrderSystem(Duration.ofMillis(100), Duration.ZERO);
        String id = system.placeOrder("alice");
        system.pay(id);

        OrderView order = await().atMost(WAIT).pollInterval(POLL)
                .ignoreException(ReadErrors.NotFound.class)
                .until(() -> system.getOrder(id), o -> o.status().isAtLeast(Status.PAID));

        assertThat(order.customer()).isEqualTo("alice");
    }

    @Test
    void timeout_carriesTheAliasAndTheLastAssertion() {
        system = new FakeOrderSystem(Duration.ofMillis(50), Duration.ZERO);
        String id = system.placeOrder("alice");   // never paid

        assertThatThrownBy(() -> await(id + " paid").atMost(Duration.ofMillis(300)).pollInterval(POLL)
                .ignoreException(ReadErrors.NotFound.class)
                .untilAsserted(() -> assertThat(system.getOrder(id).status()).isEqualTo(Status.PAID)))
                .isInstanceOf(ConditionTimeoutException.class)
                .hasMessageContaining("order-1 paid")
                .hasMessageContaining("NEW");
    }

    @Test
    void anExceptionThatIsNotIgnored_failsAtOnce() {
        system = new FakeOrderSystem(Duration.ofMillis(50), Duration.ZERO);
        String id = system.placeOrder("alice");
        system.breakReads(new ReadErrors.Unauthorized());
        long start = System.nanoTime();

        assertThatThrownBy(() -> await().atMost(WAIT).pollInterval(POLL)
                .ignoreException(ReadErrors.NotFound.class)
                .until(() -> system.getOrder(id) != null))
                .isInstanceOf(ReadErrors.Unauthorized.class);   // rethrown as is, at the first read
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void pollInSameThread_keepsAThreadBoundClientWorking() {
        system = new FakeOrderSystem(Duration.ofMillis(50), Duration.ZERO);
        String id = system.placeOrder("alice");
        ThreadBoundClient client = new ThreadBoundClient(system);

        OrderView order = await().atMost(WAIT).pollInterval(POLL).pollInSameThread()
                .ignoreException(ReadErrors.NotFound.class)
                .until(() -> client.getOrder(id), o -> true);

        assertThat(order.id()).isEqualTo(id);
    }

    /** Absence cannot be checked at one instant: "not there yet" looks the same. Hold the condition for a window. */
    @Test
    void during_provesAbsenceOverAWindow_notAtOneInstant() {
        system = new FakeOrderSystem(Duration.ofMillis(100), Duration.ZERO);
        String bobs = system.placeOrder("bob");

        await("bob's order never shows in alice's search").during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(1))
                .pollInterval(POLL)
                .until(() -> system.search("alice").stream().noneMatch(o -> o.id().equals(bobs)));

        assertThat(system.search("bob")).extracting(OrderView::id).containsExactly(bobs);   // the window covered the lag
    }
}
