package com.study.principles.concurrency.events.webhook;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The SUT: a payment provider that calls back over <b>real HTTP</b> (JDK {@link HttpClient}), as Stripe or
 * a PSP does. {@code charge} answers at once; the {@code payment.succeeded} webhook follows later from the
 * provider's own thread, with the usual delivery contract:
 * <ul>
 *   <li>headers {@code Webhook-Id} (the event id, the same on every retry), {@code Webhook-Attempt},
 *       {@code Webhook-Signature} (HMAC of the body with the endpoint's secret);</li>
 *   <li>a delivery succeeds only on <b>2xx within {@code deliveryTimeout}</b>. A slow answer counts as a failure,
 *       even if the receiver processed the event;</li>
 *   <li>failures are retried up to {@code maxAttempts} with a growing backoff: <b>at-least-once</b>, so the
 *       receiver must expect duplicates.</li>
 * </ul>
 * {@link #deliveries()} is the provider's delivery log (the "webhook attempts" page of a real dashboard).
 */
public final class FakePaymentProvider implements AutoCloseable {

    /** One attempt and what the provider made of it: an HTTP status, {@code timeout} or {@code io-error}. */
    public record Delivery(String eventId, int attempt, String outcome) {
    }

    private record Endpoint(URI url, String secret) {
    }

    private final Duration latency;
    private final Duration deliveryTimeout;
    private final int maxAttempts;
    private final Duration backoff;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    private final List<Endpoint> endpoints = new CopyOnWriteArrayList<>();
    private final List<Delivery> deliveries = new CopyOnWriteArrayList<>();
    private final AtomicLong ids = new AtomicLong();
    private final AtomicInteger settled = new AtomicInteger();
    private final ScheduledExecutorService sender = Executors.newScheduledThreadPool(4, r -> {
        Thread t = new Thread(r, "provider-webhook-sender");
        t.setDaemon(true);
        return t;
    });

    /** 20 ms latency, 300 ms delivery timeout, 4 attempts, 50 ms backoff step. */
    public FakePaymentProvider() {
        this(Duration.ofMillis(20), Duration.ofMillis(300), 4, Duration.ofMillis(50));
    }

    public FakePaymentProvider(Duration latency, Duration deliveryTimeout, int maxAttempts, Duration backoff) {
        this.latency = latency;
        this.deliveryTimeout = deliveryTimeout;
        this.maxAttempts = maxAttempts;
        this.backoff = backoff;
    }

    /** The "add endpoint" page of the provider: where to send events, and the secret to sign them with. */
    public void registerWebhook(URI url, String secret) {
        endpoints.add(new Endpoint(url, secret));
    }

    /** Returns the payment id at once; the webhook follows after the latency. */
    public String charge(String orderId) {
        long n = ids.incrementAndGet();
        String paymentId = "pay_" + n;
        String eventId = "evt_" + n;
        String body = "{\"type\":\"payment.succeeded\",\"paymentId\":\"" + paymentId + "\",\"orderId\":\"" + orderId + "\"}";
        for (Endpoint endpoint : endpoints) {
            sender.schedule(() -> deliver(endpoint, eventId, body, 1), latency.toNanos(), TimeUnit.NANOSECONDS);
        }
        return paymentId;
    }

    public List<Delivery> deliveries() {
        return List.copyOf(deliveries);
    }

    /** Events whose delivery is over: succeeded, or out of attempts. */
    public int settledCount() {
        return settled.get();
    }

    @Override
    public void close() {
        sender.shutdownNow();
    }

    private void deliver(Endpoint endpoint, String eventId, String body, int attempt) {
        HttpRequest request = HttpRequest.newBuilder(endpoint.url())
                .timeout(deliveryTimeout)
                .header("Content-Type", "application/json")
                .header("Webhook-Id", eventId)
                .header("Webhook-Attempt", String.valueOf(attempt))
                .header("Webhook-Signature", Signatures.sign(endpoint.secret(), body))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        String outcome;
        boolean delivered = false;
        try {
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            outcome = String.valueOf(status);
            delivered = status / 100 == 2;
        } catch (HttpTimeoutException e) {
            outcome = "timeout";
        } catch (IOException e) {
            outcome = "io-error";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        deliveries.add(new Delivery(eventId, attempt, outcome));
        if (delivered || attempt >= maxAttempts) {
            settled.incrementAndGet();
            return;
        }
        sender.schedule(() -> deliver(endpoint, eventId, body, attempt + 1),
                backoff.toNanos() * attempt, TimeUnit.NANOSECONDS);
    }
}
