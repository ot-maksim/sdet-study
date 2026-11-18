package com.study.principles.concurrency.eventual.good;

import com.study.principles.concurrency.eventual.FakeOrderSystem;
import com.study.principles.concurrency.eventual.OrderView;
import com.study.principles.concurrency.eventual.OrderView.Status;
import com.study.principles.concurrency.eventual.ReadErrors;
import com.study.principles.concurrency.eventual.ThreadBoundClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The hand-rolled {@link Poll} and {@link OrderChecks}: the scenarios of {@code bad.BadEventualTest}, done right. */
class GoodEventualTest {

    private FakeOrderSystem system;

    @AfterEach
    void close() {
        system.close();
    }

    @Test
    void orderVisible_returnsSoonAfterTheLag_notAfterAFixedPause() {
        system = new FakeOrderSystem(Duration.ofMillis(200), Duration.ZERO);
        OrderChecks checks = new OrderChecks(system, Duration.ofSeconds(5));
        long start = System.nanoTime();

        OrderView order = checks.orderVisible(system.placeOrder("alice"));

        assertThat(order.status()).isEqualTo(Status.NEW);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(600));
    }

    @Test
    void statusAtLeastPaid_passesWhenTheReadSideJumpedStraightToShipped() {
        system = new FakeOrderSystem(Duration.ofMillis(50), Duration.ZERO);
        OrderChecks checks = new OrderChecks(system, Duration.ofSeconds(5));
        String id = system.placeOrder("alice");
        checks.orderVisible(id);

        system.pay(id);
        OrderView order = checks.statusAtLeast(id, Status.PAID);

        assertThat(order.status()).isEqualTo(Status.SHIPPED);   // asserted on the value the poll saw
    }

    @Test
    void unexpectedError_failsAtOnce_notAfterTheTimeout() {
        system = new FakeOrderSystem(Duration.ofMillis(50), Duration.ZERO);
        String id = system.placeOrder("alice");
        system.breakReads(new ReadErrors.Unauthorized());
        long start = System.nanoTime();

        assertThatThrownBy(() -> new OrderChecks(system, Duration.ofSeconds(5)).orderVisible(id))
                .isInstanceOf(ReadErrors.Unauthorized.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void timeout_saysWhatItWaitedFor_andTheLastError() {
        system = new FakeOrderSystem(Duration.ofSeconds(10), Duration.ZERO);   // the projector is stuck
        String id = system.placeOrder("alice");

        assertThatThrownBy(() -> new OrderChecks(system, Duration.ofMillis(300)).orderVisible(id))
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("order-1 in the read model: not within PT0.3S (")
                .hasMessageEndingWith("reads); last error: "
                        + "com.study.principles.concurrency.eventual.ReadErrors$NotFound: 404 Not Found: order-1");
    }

    @Test
    void timeout_saysTheLastValue_whenTheOrderIsThereButNotInTheState() {
        system = new FakeOrderSystem(Duration.ofMillis(50), Duration.ZERO);
        String id = system.placeOrder("alice");   // never paid

        assertThatThrownBy(() -> new OrderChecks(system, Duration.ofMillis(300)).statusAtLeast(id, Status.PAID))
                .hasMessageEndingWith("last value: OrderView[id=order-1, customer=alice, status=NEW]");
    }

    @Test
    void pollRunsOnTheCallersThread_soAThreadBoundClientWorks() {
        system = new FakeOrderSystem(Duration.ofMillis(50), Duration.ZERO);
        String id = system.placeOrder("alice");
        ThreadBoundClient client = new ThreadBoundClient(system);

        OrderView order = Poll.of(id, () -> client.getOrder(id)).ignoring(ReadErrors.NotFound.class).until(o -> true);

        assertThat(order.id()).isEqualTo(id);
    }

    @Test
    void theIntervalSetsTheLoadOnTheSut() {
        system = new FakeOrderSystem(Duration.ofSeconds(1), Duration.ZERO);
        String id = system.placeOrder("alice");

        Poll.of(id, () -> system.getOrder(id)).ignoring(ReadErrors.NotFound.class)
                .every(Duration.ofMillis(100)).atMost(Duration.ofSeconds(5)).until(o -> true);

        assertThat(system.reads()).as("reads in ~1 s at 100 ms").isBetween(5, 15);   // at 1 ms: ~1000
    }
}
