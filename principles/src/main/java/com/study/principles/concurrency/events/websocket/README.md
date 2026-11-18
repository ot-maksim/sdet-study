# Events over WebSocket: push, no replay, and a connection that can go away

The server **pushes** to whoever is subscribed at the moment a message is published. There is no log and no
offset: what nobody received is gone. Everything in this kata follows from that, plus the rules of the JDK
client: be **acked** before acting, keep **demand** up, assemble **fragments**, and turn a **lost connection**
into an immediate, explained failure instead of a timeout.

Part of [`concurrency/events`](../README.md). The core (thread-safe buffer, `EventWaiter.pollMatching`, why
assertions stay in the test thread) is explained in [`kafka`](../kafka/README.md); here are only the
WebSocket-specific parts.

| Package | What to look at |
|---|---|
| `websocket` | `FakeWsServer` (in-memory server handing out the **real** `java.net.http.WebSocket`; subscribe latency, frame size, `dropAll` = restart), `FakeOrderStatusService` (the SUT: `pay` answers at once, pushes `OrderStatus` later; `status` = the REST fallback), `Messages` (the subscribe/ack protocol), `WebSocketEventsDemo` |
| `bad` | `WsEventClient`: `subscribe` = "frame sent", `onClose` ignored, send futures dropped, `join()` without timeout. `NoDemandListener`: forgets `request(1)`. `FragmentBlindListener`: ignores `last` |
| `good` | `WsEventClient` (subscribe waits for the ack, fragments + demand in `finally`, `onClose` → fail everything at once, timeouts on every future), `WsSubscription` (owns its queue, `await` with a "connection lost" pill), `ConnectionLostException` |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.concurrency.events.websocket.WebSocketEventsDemo
mvn -q -pl principles test -Dtest='com.study.principles.concurrency.events.websocket.**.*Test'
```

`WebSocketEventsDemo`, typical output (the server takes 0–30 ms to register a subscription, the SUT pushes
0–20 ms after `pay`):
```
bad: subscribe = frame sent, then pay        22/30 ok
good: subscribe = server acked, then pay     30/30 ok
bad: server restarts while the test waits    gave up after 3010 ms: "no OrderStatus for order-x"
good: server restarts while the test waits   failed after   106 ms: WebSocket closed (1001 Going away) while waiting on 'orders'
```
The first line is a flaky test that passes most of the time: the worst kind. The last two lines are the
same failure: one costs the full timeout and blames the SUT, the other costs 100 ms and names the cause.

## 1. The fake is the JDK API

`FakeWsServer.connect(listener)` stands in for `HttpClient.newWebSocketBuilder().buildAsync(uri, listener)`
and returns a real `java.net.http.WebSocket`. The clients are written against the JDK interfaces only, so
with a real server (stage 4) only the connect line changes. The callbacks run on `ws-server-io`, as they run
on the `HttpClient`'s executor with a real server: a foreign thread, the same as Kafka's poll thread.

## 2. Ready = acked, not "frame sent"

`ws.sendText(subscribe)` completes when the frame is **handed over**. The server registers the subscription
later, and only pushes **after** that reach the client. With no replay, a push in between is lost for good
(`subscribeWithoutWaitingForTheAck_pushRightAfterGoesToNobody`).

`good.WsEventClient.subscribe` registers the subscription locally, sends the frame, and **blocks until the
server's ack**. After it returns, the test may act at once.

Real protocols have the ack, under different names: STOMP `SUBSCRIBE` with a `receipt` header (Spring),
Socket.IO acknowledgements, `subscription_ack`-style messages in GraphQL-over-WebSocket or exchange APIs.
**No ack in the protocol?** Then there is no exact "ready": publish a canary message through the channel
and wait for it, or wait for a server-side fact (`subscriberCount` here, an admin endpoint there). A fixed
`sleep` after subscribe is the guess that the demo's first line measures.

## 3. Demand: `request(n)` or silence

The JDK delivers exactly as many listener invocations as were requested. The default `onOpen` asks for one,
the default `onText` asks for the next; an `onText` override that does not call `request(1)` gets one
message and then nothing, with no exception (`listenerWithoutRequest_getsOneMessageAndThenNothing`: the
ack used up the only demand, so not a single event arrives).

`good.WsEventClient` calls `request(1)` in a `finally`: a failure while handling one message must not stall
the connection. Demand counts **invocations**, not messages: every fragment uses one.

## 4. Fragments: a message ends at `last == true`

`onText(data, last)` may carry part of a message: long payloads, small buffers, a proxy. Treating each call
as a message gives pieces of JSON (`listenerIgnoringLast_seesPiecesInsteadOfMessages`). A substring match
can even "find" the order id in half a message and pass. Append to a `StringBuilder` until `last`, then
dispatch. The builder is touched only by the io thread, so it needs no lock.

## 5. A lost connection is a failure with a reason

Servers restart, proxies cut idle connections, deploys roll. Without `onClose`/`onError` handling
(`bad.WsEventClient`) a waiting test runs into its full timeout and reports "no push for order-1": the
investigation starts at the SUT, which did nothing wrong.

`good.WsEventClient` on `onClose`/`onError`:
- completes `closed` with the code and reason; every later call fails at once (`ConnectionLostException`);
- fails pending subscribes (`connectionDropsWhileSubscribing_subscribeFailsWithTheReason_notWithATimeout`);
- puts a **pill** into every subscription's queue. The waiting test is blocked in `queue.poll`, and an item
  in the queue wakes it **now**; a flag would only be seen at the end of the timeout. `await` puts the pill
  back, so every next wait fails too.

The bad client also drops the futures of `sendText`: a send on a closed connection fails in a future nobody
reads, so `subscribe` "succeeds". A future you do not look at is an exception you swallowed.

## 6. Reconnect: the gap is not recoverable

Messages pushed while the client was not connected (or not yet re-subscribed) are **gone**
(`afterReconnect_pushFromTheGapIsGone_soCheckTheStateViaTheApi`). So:

- **No silent auto-reconnect in a test client.** If the client reconnects behind the test's back, "not
  received" is ambiguous: did the SUT not push, or did it push into the gap? The good client fails, and the test
  (or a retry policy) decides.
- **State over events after a gap.** The SUT's state survived (`status(id)` = `GET /orders/{id}`): assert the
  outcome through the API, and the push only when the connection was up the whole time.
- Real servers sometimes offer resume (Socket.IO connection-state recovery, a `lastEventId` in SSE, a
  sequence number to resubscribe from). That is replay built on top: use it if the SUT has it; do not assume it.

## 7. Unsubscribe and close

`WsSubscription` owns its queue. After `close()` the client forgets the channel and drops late messages for
it in dispatch: nothing a late message could spoil, so no `SwitchableListener` is needed (compare the Kafka
kata, where the listener wrote to a resource the test released). `WsEventClient.close()` sends a close
frame and waits for the server's answer with a timeout, then aborts: a test must not hang in cleanup.

## WebSocket vs Kafka

| | Kafka | WebSocket |
|---|---|---|
| model | pull: the client polls a log | push: the server calls the listener |
| ready | positioned at the end of the topic | the server acked the subscription |
| missed a message | still in the log: seek back | gone |
| flow control | `max.poll.records`, the client's loop | `request(n)` per invocation |
| connection | stateless polling, reconnects transparently | one connection; its loss must be handled |
| unsubscribe | remove the listener | unsubscribe frame; closing the connection ends everything |

## Pros / cons

| Pros | Cons |
|---|---|
| A push arrives as fast as the SUT sends it: no polling, no sleeps | Ready needs a protocol-level ack; without one there is no exact answer |
| Connection loss is a fast, named failure | One more stateful resource per test class: connect, close, handle loss |
| The client is plain JDK: no WebSocket library, the fake swaps for a real server | The JDK API is low level: demand and fragments are on you (a library hides them, and its own bugs) |
| `WsSubscription` owns its queue: unsubscribe is safe by construction | No replay: anything about the past must go through the API |

## When NOT to apply

- **Only the final state matters.** If the test is "the order becomes PAID", poll the API (`concurrency/eventual`):
  no connection to manage, no gap to worry about. Use the WebSocket when the **push itself** is the feature
  (live UI updates, notifications, a trading feed).
- **The UI already listens.** For a page that renders pushes, assert what the page shows (`events/ui`),
  not the raw socket underneath.
- **Do not reconnect inside the test client to "make it stable".** It turns a loud failure into an ambiguous one.
- **No custom client if the team already has one** (Spring's `StompSession`, a Socket.IO client): check it against
  sections 2–6 instead of rewriting it.

## Try it yourself

1. Remove the `finally` around `webSocket.request(1)` and make `dispatch` throw for a message without a
   `channel` field. Which good test catches the stalled connection, and how long does it take to fail?
2. Add a `canary` mode to `subscribe` for a protocol without acks: publish a unique message through the channel
   and wait for it. What does it need from the server, and why is it still better than a sleep?
3. `WsSubscription.await` drops non-matching messages. Write a test that listens for `PAID` and then `SHIPPED`
   for one order with two `await`s on one subscription. What breaks if `SHIPPED` for another order comes in between,
   and what if your own `SHIPPED` arrives before `PAID`?
4. The fake delivers `onClose` regardless of demand. Read the "receive methods" part of the
   `java.net.http.WebSocket` Javadoc: does the JDK? If not, make the fake match it. Which part of
   `good.WsEventClient` keeps the pill working, and what would break in a listener that stops requesting?
5. Smell check on your framework: `grep -rn 'buildAsync\|onText\|sendText' --include=*.java`. For each listener,
   answer: who calls `request`, where are fragments joined, what happens in `onClose`?
