# Webhooks: the test is the server

With a webhook the roles are inverted: the SUT (a payment provider, a CI system, a partner API) is the
**HTTP client**, and the test has to **be the server**. A server has its own rules: it needs a free port,
an address the SUT can reach, an answer within the caller's timeout, and it gets **retries**, so the same event
arrives more than once. The handler runs on the server's thread, where an assertion fails nobody.

Part of [`concurrency/events`](../README.md). The core (thread-safe buffer, `EventWaiter.pollMatching`, match on
the test's own id) is explained in [`kafka`](../kafka/README.md). This kata runs **real HTTP** on the loopback
interface: JDK `com.sun.net.httpserver.HttpServer` for the receiver, JDK `HttpClient` in the fake provider.
Nothing leaves the machine.

| Package | What to look at |
|---|---|
| `webhook` | `FakePaymentProvider` (the SUT: `charge` answers at once, then POSTs `payment.succeeded` with `Webhook-Id`, `Webhook-Attempt`, `Webhook-Signature`; 2xx within 300 ms or retry, 4 attempts; `deliveries()` = its delivery log), `Webhook` (one delivery), `Signatures` (HMAC-SHA256 over the raw body), `WebhookEventsDemo` |
| `bad` | `WebhookReceiver`: fixed port, the test's listener runs **in the handler before the answer**, no executor |
| `good` | `WebhookReceiver`: port 0 on loopback, record → answer at once → the test checks in its own thread; own executor; all attempts kept, `distinctEvents()`; `respondNext(status, n)` to script failures |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.concurrency.events.webhook.WebhookEventsDemo
mvn -q -pl principles test -Dtest='com.study.principles.concurrency.events.webhook.**.*Test'
```

`WebhookEventsDemo`, one charge received four ways:
```
bad: test code in the handler (400 ms)       test saw 4 deliveries; provider: [timeout, timeout, timeout, timeout]
good: record, answer, then the test works    test saw 1 deliveries; provider: [204]
bad: assertion in the handler                test saw 0 deliveries; provider: [io-error, io-error, io-error, io-error]
    the test reports: no webhook within the timeout; the AssertionError: nowhere
good: assertion in the test thread           test saw 1 deliveries; provider: [204]
    the test reports: AssertionError: signature does not match whsec_test
```
The first line is the worst of both worlds: the test processed the event **four times**, and the provider
thinks **every delivery failed**. In a real system that marks the endpoint unhealthy and, after enough
failures, disables it.

## 1. Port 0, and an address the SUT can reach

A fixed port (`webhook.port=8085`) clashes with parallel tests, with a second CI job on the agent, with a
receiver a crashed test never stopped: `BindException: Address already in use`
(`fixedPort_aSecondReceiverOnTheSamePort_failsToBind`). Bind to port **0**: the OS picks a free port, and
`url()` reads it back. **Register the URL after binding**: that order is the webhook's "subscribe before the action".

The address matters as much as the port. `127.0.0.1` works when the SUT runs on the same machine (as here).
Otherwise:

| The SUT runs... | The receiver must... |
|---|---|
| on the same host | bind loopback, register `http://127.0.0.1:<port>` |
| in Docker on the same host | bind `0.0.0.0`, register `http://host.docker.internal:<port>` (Linux: `--add-host=host.docker.internal:host-gateway`) |
| on a shared stand / in a cluster | be reachable from it: a tunnel, a receiver deployed next to the SUT, or the SUT's own "webhook inspector" endpoint to query |
| at a third party (a real PSP sandbox) | be public (a tunnel), or do not receive at all: query the provider's events API instead |

## 2. Answer first, think later

The provider waits for **2xx within its timeout**; anything else is a failed delivery and a retry. The bad
receiver calls the test's listener **inside the handler, before answering**: a DB check, a strict parser or a
breakpoint longer than 300 ms makes every delivery time out
(`slowListener_providerTimesOutAndRetries_testSeesOneEventSeveralTimes`). Without an executor all requests
share one dispatcher thread, so the retries queue up behind the slow one and time out too.

The good handler only **records** the delivery (`received` list and `inbox` queue) and **answers 204 at once**. The
test waits with `await` and does its checks in its own thread, as slowly as it likes
(`theReceiverAnswersAtOnce_soASlowTestCausesNoRetries`). That is the same rule as for production webhook
consumers: acknowledge fast, process asynchronously.

## 3. An assertion in the handler fails nobody

On JDK 17, an `AssertionError` thrown in an `HttpHandler` is swallowed by the server: the connection is
dropped, the provider sees `HTTP/1.1 header parser received no bytes` ("io-error") and retries, and the test
times out waiting for a webhook that arrived four times
(`assertionInTheHandler_vanishes_theProviderSeesIoErrorsAndTheTestSeesNothing`). The real reason, a wrong
signature, is in no report.

Assert **in the test thread** on what the receiver recorded (`wrongSignature_failsInTheTestThread_withTheReason`).
The good receiver is a recorder, not a validator: it answers 204 even to a badly signed delivery, because
deciding is the test's job. (A production consumer answers 400/401; a test receiver that rejects would make the
provider retry, which is a different test.)

**Verify the signature over the raw body**, before parsing: re-serialized JSON is not the same bytes. Compare
with `MessageDigest.isEqual`, not `equals`.

## 4. At-least-once: count events, not deliveries

Retries make duplicates normal, not exceptional. Even a fast receiver gets them: a 2xx lost on the network
looks like a timeout to the provider. So:

- `await(predicate)` returns the **first** matching delivery: enough for "the webhook arrived".
- `distinctEvents()` dedups by `Webhook-Id`: use it for "exactly these events"
  (`duplicates_distinctEventsCountsEventsNotDeliveries`). Never dedup by body: two real events can have the same body.
- `received()` keeps **all** attempts: use it to test the SUT's own delivery contract.

The receiver is also a **tool for testing the SUT**: `respondNext(503, 2)` scripts failures, and the test asserts
the retry policy (attempt numbers, the same event id and body every time, success on the third attempt:
`scriptedFailures_showTheSutsRetryPolicy`). Stretch it: assert the backoff between `receivedAt`s, or that after
the last attempt the SUT gives up.

## 5. Lifecycle

`WebhookReceiver.start()` returns listening; `close()` stops the server (`stop(0)`, no grace period) and the handler
threads. One receiver per test, or per class with the predicate on the test's own id (an order id here); a
shared receiver across tests works only with that predicate, as any shared channel. 405 for anything but POST,
so a health check or a browser does not land in the inbox.

## Webhook vs Kafka vs WebSocket

| | Kafka | WebSocket | Webhook |
|---|---|---|---|
| who is the server | the broker | the SUT | **the test** |
| ready | positioned at the end | the server acked | listening, URL registered in the SUT |
| missed an event | still in the log | gone | **retried** by the SUT (until it gives up) |
| duplicates | on replay / rebalance | rare | **normal**: at-least-once |
| the test's code runs on | the poll thread | the io thread | the HTTP server's thread, **while the SUT waits** |

## Pros / cons

| Pros | Cons |
|---|---|
| Real HTTP: what the SUT does is what the test sees (headers, retries, timeouts) | The SUT must reach the test machine: easy locally, a project of its own in a cluster |
| Port 0 + loopback: parallel-safe, no config | One more server per test class to start and stop |
| The receiver doubles as a tool to test the SUT's retry policy | Duplicates are normal: every assertion must say whether it counts events or deliveries |
| JDK only: `HttpServer` + `HttpClient`, no WireMock | `com.sun.net.httpserver` is basic (no TLS setup, no routing): fine for a receiver, not for a mock API |

## When NOT to apply

- **The SUT cannot reach the test.** Do not open a tunnel from CI to a third party just to receive a webhook:
  query the provider's events API, or the SUT's own record of what it sent.
- **You need request matching, stubs and verification for many endpoints.** That is WireMock or MockServer (stage 4);
  this receiver is for one inbound callback.
- **Only the effect matters.** If the webhook makes the SUT change state, assert the state through the API
  (`concurrency/eventual`); receive the webhook when the **callback itself** is the contract.
- **Do not make the test receiver reject bad signatures** to "fail fast": the provider retries, the test sees
  a timeout. Record, then assert.

## Try it yourself

1. Add `awaitDistinct(predicate, count, timeout)`: wait until `count` distinct events match, failing with the list
   of ids that did arrive. Where should the dedup happen: in the handler, or in the test thread, and why?
2. Use `receivedAt` to assert the provider's backoff in `scriptedFailures_showTheSutsRetryPolicy`: what tolerance
   makes the check stable on a slow CI agent (see `principles/time`)?
3. Make the provider's retries **concurrent** with a slow first attempt (send attempt 2 while attempt 1 is still
   waiting). Which of the bad receiver's problems gets worse, and does the good one change?
4. Run `GoodWebhookTest` with `@Execution(CONCURRENT)`. What makes it parallel-safe, and what would break
   if `evt_1` ids came from a static counter shared by all providers?
5. Smell check on your framework: `grep -rn 'HttpServer.create\|new InetSocketAddress(8\|createContext' --include=*.java`.
   For each receiver: which port, which address, what runs before the answer, where are assertions?
