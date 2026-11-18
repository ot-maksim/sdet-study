package com.study.principles.concurrency.events.webhook;

import com.study.principles.concurrency.events.webhook.FakePaymentProvider.Delivery;
import com.study.principles.concurrency.events.webhook.good.WebhookReceiver;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * One charge, one {@code payment.succeeded} webhook, received four ways. The provider waits 300 ms for a 2xx and
 * retries up to 4 times.
 *
 * <pre>
 * mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.concurrency.events.webhook.WebhookEventsDemo
 * </pre>
 */
public final class WebhookEventsDemo {

    private static final String SECRET = "whsec_test";

    public static void main(String[] args) throws Exception {
        slowListenerInTheHandler();
        recordAndAnswer();
        assertionInTheHandler();
        assertionInTheTest();
    }

    private static void slowListenerInTheHandler() throws Exception {
        List<Webhook> seen = new CopyOnWriteArrayList<>();
        try (var provider = new FakePaymentProvider();
             var receiver = new com.study.principles.concurrency.events.webhook.bad.WebhookReceiver(freePort(), w -> {
                 sleep(400);
                 seen.add(w);
             })) {
            provider.registerWebhook(receiver.url(), SECRET);
            provider.charge("order-1");
            waitSettled(provider);
            sleep(500);   // let the dispatcher finish the queued retries
            print("bad: test code in the handler (400 ms)", seen.size(), provider);
        }
    }

    private static void recordAndAnswer() throws Exception {
        try (var provider = new FakePaymentProvider();
             var receiver = WebhookReceiver.start()) {
            provider.registerWebhook(receiver.url(), SECRET);
            provider.charge("order-1");
            receiver.await(w -> "order-1".equals(w.field("orderId")), Duration.ofSeconds(5));
            sleep(400);   // the test being slow changes nothing
            waitSettled(provider);
            print("good: record, answer, then the test works", receiver.received().size(), provider);
        }
    }

    private static void assertionInTheHandler() throws Exception {
        List<Webhook> seen = new CopyOnWriteArrayList<>();
        try (var provider = new FakePaymentProvider();
             var receiver = new com.study.principles.concurrency.events.webhook.bad.WebhookReceiver(freePort(), w -> {
                 if (!w.hasValidSignature(SECRET)) {
                     throw new AssertionError("signature does not match " + SECRET);
                 }
                 seen.add(w);
             })) {
            provider.registerWebhook(receiver.url(), "whsec_rotated");
            provider.charge("order-1");
            waitSettled(provider);
            print("bad: assertion in the handler", seen.size(), provider);
            System.out.println("    the test reports: no webhook within the timeout; the AssertionError: nowhere");
        }
    }

    private static void assertionInTheTest() throws Exception {
        try (var provider = new FakePaymentProvider();
             var receiver = WebhookReceiver.start()) {
            provider.registerWebhook(receiver.url(), "whsec_rotated");
            provider.charge("order-1");
            Webhook webhook = receiver.await(w -> "order-1".equals(w.field("orderId")), Duration.ofSeconds(5)).orElseThrow();
            waitSettled(provider);
            print("good: assertion in the test thread", receiver.received().size(), provider);
            System.out.println("    the test reports: " + (webhook.hasValidSignature(SECRET)
                    ? "ok" : "AssertionError: signature does not match " + SECRET));
        }
    }

    private static void print(String name, int seenByTest, FakePaymentProvider provider) {
        String outcomes = provider.deliveries().stream().map(Delivery::outcome).collect(Collectors.joining(", "));
        System.out.printf("%-44s test saw %d deliveries; provider: [%s]%n", name, seenByTest, outcomes);
    }

    private static void waitSettled(FakePaymentProvider provider) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (provider.settledCount() < 1 && System.nanoTime() < deadline) {
            sleep(10);
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
