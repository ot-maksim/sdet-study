package com.study.principles.concurrency.events.webhook.bad;

import com.study.principles.concurrency.Await;
import com.study.principles.concurrency.events.webhook.FakePaymentProvider;
import com.study.principles.concurrency.events.webhook.FakePaymentProvider.Delivery;
import com.study.principles.concurrency.events.webhook.Webhook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Characterization tests: they pass <i>because</i> they pin the bugs of {@link WebhookReceiver}. */
class BadWebhookTest {

    private static final String SECRET = "whsec_test";
    private static final Duration WAIT = Duration.ofSeconds(10);

    private final FakePaymentProvider provider = new FakePaymentProvider();
    private final List<AutoCloseable> toClose = new ArrayList<>(List.of(provider));

    /** Reverse order, as try-with-resources. */
    @AfterEach
    void closeAll() throws Exception {
        for (int i = toClose.size() - 1; i >= 0; i--) {
            toClose.get(i).close();
        }
    }

    private <T extends AutoCloseable> T closeLater(T closeable) {
        toClose.add(closeable);
        return closeable;
    }

    /** Stands in for the configured 8085, so the test does not depend on what else runs on this machine. */
    private static int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    // --- 1. Fixed port -----------------------------------------------------------------------------------

    @Test
    void fixedPort_aSecondReceiverOnTheSamePort_failsToBind() throws IOException {
        int configuredPort = freePort();
        closeLater(new WebhookReceiver(configuredPort, w -> { }));   // a parallel test, or one that never closed

        assertThatThrownBy(() -> new WebhookReceiver(configuredPort, w -> { }))
                .isInstanceOf(UncheckedIOException.class)
                .hasRootCauseInstanceOf(BindException.class);
    }

    // --- 2. The test's code before the answer --------------------------------------------------------------

    @Test
    void slowListener_providerTimesOutAndRetries_testSeesOneEventSeveralTimes() throws IOException {
        List<Webhook> received = new CopyOnWriteArrayList<>();
        var receiver = closeLater(new WebhookReceiver(freePort(), w -> {
            sleep(Duration.ofMillis(400));   // "check it in the DB", longer than the provider's 300 ms timeout
            received.add(w);
        }));
        provider.registerWebhook(receiver.url(), SECRET);

        provider.charge("order-1");
        Await.until(() -> provider.settledCount() == 1, WAIT);
        Await.until(() -> received.size() >= 2, WAIT);

        assertThat(received).extracting(Webhook::eventId).containsOnly("evt_1");   // one event, several deliveries
        assertThat(provider.deliveries()).first().extracting(Delivery::outcome).isEqualTo("timeout");
    }

    // --- 3. An assertion in the handler ---------------------------------------------------------------------

    @Test
    void assertionInTheHandler_vanishes_theProviderSeesIoErrorsAndTheTestSeesNothing() throws IOException {
        List<Webhook> received = new CopyOnWriteArrayList<>();
        var receiver = closeLater(new WebhookReceiver(freePort(), w -> {
            assertThat(w.hasValidSignature(SECRET)).as("signed with %s", SECRET).isTrue();
            received.add(w);
        }));
        provider.registerWebhook(receiver.url(), "whsec_rotated");   // the secret was rotated on the provider's side

        provider.charge("order-1");
        Await.until(() -> provider.settledCount() == 1, WAIT);

        assertThat(received).as("the test would report: no webhook arrived").isEmpty();
        assertThat(provider.deliveries()).extracting(Delivery::outcome).containsOnly("io-error").hasSize(4);
        // The AssertionError with the real reason ("signed with whsec_test") reached nobody.
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
