package com.study.principles.concurrency.events.webhook.good;

import com.study.principles.concurrency.Await;
import com.study.principles.concurrency.events.webhook.FakePaymentProvider;
import com.study.principles.concurrency.events.webhook.FakePaymentProvider.Delivery;
import com.study.principles.concurrency.events.webhook.Webhook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/** The scenarios of {@code bad.BadWebhookTest}, done right, plus the receiver as a tool for testing retries. */
class GoodWebhookTest {

    private static final String SECRET = "whsec_test";
    private static final Duration WAIT = Duration.ofSeconds(10);

    private final FakePaymentProvider provider = new FakePaymentProvider();
    private final WebhookReceiver receiver = WebhookReceiver.start();

    @AfterEach
    void closeAll() {
        receiver.close();
        provider.close();
    }

    private static Predicate<Webhook> forOrder(String orderId) {
        return w -> orderId.equals(w.field("orderId"));
    }

    // --- 1. Port 0 ---------------------------------------------------------------------------------------

    @Test
    void portZero_receiversOfParallelTestsRunSideBySide() {
        try (var another = WebhookReceiver.start()) {
            assertThat(another.url().getPort()).isNotEqualTo(receiver.url().getPort());
            assertThat(receiver.url().getHost()).isEqualTo("127.0.0.1");
        }
    }

    // --- 2. Record, answer, check in the test thread ---------------------------------------------------------

    @Test
    void webhookArrives_andIsCheckedInTheTestThread() throws InterruptedException {
        provider.registerWebhook(receiver.url(), SECRET);

        String paymentId = provider.charge("order-1");

        Webhook webhook = receiver.await(forOrder("order-1"), WAIT).orElseThrow();
        assertThat(webhook.hasValidSignature(SECRET)).isTrue();
        assertThat(webhook.field("paymentId")).isEqualTo(paymentId);
        assertThat(webhook.field("type")).isEqualTo("payment.succeeded");
    }

    @Test
    void wrongSignature_failsInTheTestThread_withTheReason() throws InterruptedException {
        provider.registerWebhook(receiver.url(), "whsec_rotated");

        provider.charge("order-1");
        Webhook webhook = receiver.await(forOrder("order-1"), WAIT).orElseThrow();

        assertThatThrownBy(() -> assertThat(webhook.hasValidSignature(SECRET)).as("signed with %s", SECRET).isTrue())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("signed with whsec_test");   // where the test runner shows it
    }

    @Test
    void theReceiverAnswersAtOnce_soASlowTestCausesNoRetries() throws InterruptedException {
        provider.registerWebhook(receiver.url(), SECRET);

        provider.charge("order-1");
        Thread.sleep(500);   // the test is busy with something else: the receiver does not wait for it
        Await.until(() -> provider.settledCount() == 1, WAIT);

        assertThat(provider.deliveries()).containsExactly(new Delivery("evt_1", 1, "204"));
        assertThat(receiver.received()).hasSize(1);
    }

    // --- 3. Duplicates: count events, not deliveries ------------------------------------------------------------

    @Test
    void scriptedFailures_showTheSutsRetryPolicy() {
        provider.registerWebhook(receiver.url(), SECRET);
        receiver.respondNext(503, 2);

        provider.charge("order-1");
        Await.until(() -> provider.settledCount() == 1, WAIT);

        assertThat(provider.deliveries()).extracting(Delivery::attempt, Delivery::outcome)
                .containsExactly(tuple(1, "503"), tuple(2, "503"), tuple(3, "204"));
        assertThat(receiver.received()).extracting(Webhook::eventId, Webhook::attempt)
                .containsExactly(tuple("evt_1", 1), tuple("evt_1", 2), tuple("evt_1", 3));
        assertThat(receiver.received()).extracting(Webhook::body).containsOnly(receiver.received().get(0).body());
    }

    @Test
    void duplicates_distinctEventsCountsEventsNotDeliveries() {
        provider.registerWebhook(receiver.url(), SECRET);
        receiver.respondNext(500, 1);

        provider.charge("order-1");
        provider.charge("order-2");
        Await.until(() -> provider.settledCount() == 2, WAIT);

        assertThat(receiver.received()).hasSize(3);
        assertThat(receiver.distinctEvents()).extracting(w -> w.field("orderId"))
                .containsExactlyInAnyOrder("order-1", "order-2");
    }

    // --- 4. A shared endpoint ----------------------------------------------------------------------------------

    @Test
    void await_skipsOtherTestsEvents() throws InterruptedException {
        provider.registerWebhook(receiver.url(), SECRET);

        provider.charge("order-of-a-parallel-test");
        provider.charge("order-1");

        assertThat(receiver.await(forOrder("order-1"), WAIT)).isPresent();
    }

    @Test
    void notAPost_isRejectedWithoutTouchingTheInbox() throws Exception {
        var http = java.net.http.HttpClient.newHttpClient();
        var response = http.send(java.net.http.HttpRequest.newBuilder(receiver.url()).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(receiver.received()).isEmpty();
    }
}
