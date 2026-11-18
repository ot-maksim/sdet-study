# Async events: subscribe before the action, wait for *my* event

The test triggers an action ("pay"), and the SUT answers **later, on a channel the test listens to**:
a Kafka topic, a WebSocket push, an HTTP callback (webhook), a browser event. Two rules make such tests fast
and stable on every transport:

1. **Subscribe before the action**, and only act once the subscription is *ready*. Subscribing afterwards
   races the SUT: a fast answer arrives before anybody listens and is lost.
2. **Wait for a condition with a deadline, matching on the test's own id.** Not `sleep`, not "the next
   message": channels are shared with other tests.

## The shared core (this package)

| Class | What it does |
|---|---|
| `EventWaiter.pollMatching(queue, predicate, timeout)` | takes from a `BlockingQueue` until a match or the deadline; skips noise; returns as soon as the match arrives |
| `SwitchableListener` | wraps a listener so that after `deactivate()` the delegate is not running and never will: resources can be released |

The shape is the same everywhere: **push source → listener on a foreign thread → thread-safe buffer →
`pollMatching` in the test thread**. The full reasoning (why a queue, why `synchronized`, why the close
order) is in [`kafka/README.md`](kafka/README.md), sections 1–4.

## Why there is no common `EventClient` interface

The consuming side is common and is shared above. What happens **before** the first event is not: how a
client becomes ready, what it loses, who is the server. A single interface would hide exactly the
differences that make tests flaky.

| Transport | Package | Ready means | Missed an event? | What it teaches on its own |
|---|---|---|---|---|
| Kafka | [`kafka`](kafka/README.md) | the poll thread stands at the end of the topic | replayable: seek to an older offset | pull model, one poll thread, positions, listener registry |
| WebSocket | `websocket` (next) | handshake done, server confirmed the subscription | **gone**: no replay | push model, connection lifecycle, reconnect gap, close as unsubscribe |
| Webhook | `webhook` (next) | own HTTP server listens, URL registered in the SUT | the SUT **retries**: duplicates | the test is the server: port 0, answer 2xx fast, idempotent matching, never assert in the handler |
| UI | `ui` (next) | the waiter is armed before the click | transient state (a toast) is gone before a poll sees it | "run and wait": the API wraps the action (Playwright `waitForResponse(predicate, action)`) |
