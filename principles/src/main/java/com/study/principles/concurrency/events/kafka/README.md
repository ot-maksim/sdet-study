# Events over Kafka: a listener runs on someone else's thread

A test that checks an event (Kafka, RabbitMQ, a webhook, a WebSocket) does not call the code that delivers
it: a **background thread** calls the test's listener, at a time the test does not control. Everything
follows from that: the listener must be thread-safe, the test must **wait for a condition with a deadline**
(never sleep), find **its** event among others, and stop the listener **for sure** before it releases
whatever the listener touches.

Part of [`concurrency/events`](../README.md): the transport-independent core (`EventWaiter`,
`SwitchableListener`) lives there; this package is the Kafka-shaped client and the full story of the core.
It builds on `principles/parallel` (mechanism 7, "append": concurrent collections). There the threads are
parallel tests; here one of them belongs to the infrastructure.

| Package | What to look at |
|---|---|
| `events` (parent) | `EventWaiter.pollMatching` (wait for *my* event, skip noise), `SwitchableListener` (`synchronized` switch): shared by every transport |
| `kafka` | `FakeBroker` (in-memory "Kafka": a topic is an append-only log with offsets; it never calls anybody, clients poll it), `FakeOrderService` (the SUT: answers at once, publishes `OrderCreated` later from its own thread), `Event` (immutable record handed to every listener), `KafkaEventsDemo` |
| `bad` | `EventClient`: listeners in an `ArrayList`, `subscribe` returns before reading starts, no try/catch around a listener. `VolatileSwitchableListener`: a flag without waiting |
| `good` | `EventClient` (one poll thread, commands queue, `subscribe` blocks until ready, `CopyOnWriteArrayList`, failing listeners logged), `EventSubscription` (try-with-resources: deactivate → unsubscribe, keeps the instance) |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.concurrency.events.kafka.KafkaEventsDemo
mvn -q -pl principles test -Dtest='com.study.principles.concurrency.events.kafka.**.*Test'
```

- `KafkaEventsDemo` — one check written four ways, 30 runs each, the SUT's latency 20–300 ms, other tests creating
  orders in the same topic. Typical output:
  ```
  sleep(100), then look in the list      10/30 ok, avg  103 ms per test
  sleep(400), then look in the list      30/30 ok, avg  403 ms per test
  poll(5 s): the first event is mine      0/30 ok, avg   17 ms per test
  EventWaiter.pollMatching(5 s)          30/30 ok, avg  189 ms per test
  ```
  A short sleep is flaky, a long one is slow (and flaky on the next slow CI agent); `poll` alone is fast and
  wrong; `pollMatching` is correct **and** as fast as the SUT.
- `BadKafkaEventsTest` — characterization: each test passes *because* it pins a bug. Latches script the
  interleavings, so the races are deterministic scenarios (except the `ArrayList` one, which repeats until
  it shows). Expected noise in the output: the bad client's poll thread dying with a stack trace.
- `GoodKafkaEventsTest` — the same scenarios with the good parts, plus the guarantees of `good.EventClient`.

## 1. The callback runs on the poll thread

`received::add` looks like the test's code, but it runs on `event-client-poll`. The collection is shared
between that thread (writes) and the test thread (reads), and between several poll threads if the test
listens through more than one client.

| Collect into | Verdict |
|---|---|
| `ArrayList` | **no**: lost updates and `ArrayIndexOutOfBoundsException` with two writers (`arrayList_fedByTwoPollThreads_losesEvents`); with one writer, no visibility guarantee for the reader and `ConcurrentModificationException` when AssertJ iterates while the listener adds |
| `Collections.synchronizedList` | works for `add`, but iteration (every AssertJ assertion) needs a manual `synchronized (list)` |
| `CopyOnWriteArrayList` | good for "assert the set of events" with Awaitility; every `add` copies the array: fine for tens, not for thousands |
| `LinkedBlockingQueue` | **default**: safe, and it **waits**: `poll(timeout)` returns as soon as an event arrives |

The listener must also be **fast**: it blocks the poll thread, so every other subscription waits. Parse,
assert and call the SUT in the test thread, after taking the event from the queue.

## 2. Waiting: a condition with a deadline

| Style | Problem |
|---|---|
| assert right after the action | the event is still in the SUT's outbox: always empty |
| `Thread.sleep(n)` then assert | too short → flaky; long enough → every run pays `n`; "enough" changes with the CI agent |
| `queue.poll(timeout)` and assert on it | returns the **first** event in the topic: in a shared topic, another test's |
| `EventWaiter.pollMatching(queue, predicate, timeout)` | returns at the **first matching** event; the timeout is an upper bound, not a pause; `Optional.empty()` lets the assertion say what was missing |
| Awaitility `untilAsserted` over a `CopyOnWriteArrayList` | good for sets of events ("both OrderCreated and PaymentReserved"); see [`concurrency/eventual`](../../eventual/README.md) for its settings |

Match on what **this test** created (order id as the key, a correlation id header), never on "the next event".
`pollMatching` drops the noise it skips; if two waits share one queue, the second loses what the first
dropped: use one queue per expectation, or collect into a list and wait with Awaitility.

## 3. Listener registry: `CopyOnWriteArrayList` and its price

The client iterates over the listeners on the poll thread; tests add and remove them from their own threads,
and a listener may even unsubscribe itself during a dispatch ("wait for the first event, then stop").

- `ArrayList` (`bad.EventClient`): a listener removing itself mid-dispatch throws
  `ConcurrentModificationException` on the poll thread, which dies. If it was the second-to-last, there is no
  exception at all: the last listener is **silently skipped** (`hasNext()` compares `cursor != size`).
- `CopyOnWriteArrayList` (`good.EventClient`): iteration goes over a **snapshot**; removal during a dispatch is
  safe. Dispatch is frequent, subscribing is rare: exactly what COW is for.

The price of the snapshot: a listener **removed during a dispatch is still called from that dispatch**.
`plainUnsubscribeThenRelease_listenerStillCalledFromTheDispatchInProgress` writes to a report the test has
just closed.

## 4. Switching a listener off for sure

`SwitchableListener` wraps the test's logic: `accept` runs the delegate only while active; `deactivate`
switches it off. Both are `synchronized` on one monitor, so `deactivate` **waits for a running `accept`**.

A `volatile` flag (`bad.VolatileSwitchableListener`) gives visibility, not atomicity: `if (active)` and
`delegate.accept(..)` are two steps; `deactivate()` returns between them, the test releases the resource,
the delegate writes to it (`volatileFlag_deactivateReturnsWhileTheDelegateStillRuns`).

Close order (`EventSubscription.close`): **deactivate → unsubscribe → release resources**. Deactivate first:
after it nothing new starts and nothing old is running; unsubscribing first leaves the snapshot window open.

**Unsubscribe the instance you subscribed.** `client.unsubscribe(topic, received::add)` creates a new
lambda object, not equal to the subscribed one, and removes nothing; the old listener keeps feeding a queue
nobody reads, across tests. `EventSubscription` keeps the reference, so `received::add` can be passed straight in.

## 5. One failing listener, one dead thread

Without a try/catch around each listener (`bad.EventClient`), one malformed message in a shared topic and
one strict parser kill the poll thread: **every** subscription goes silent, and the failures show up later,
in other tests, as timeouts. `good.EventClient` logs and skips a failing listener, and `subscribe` on a broken
or closed client fails at once (`ensureAlive`) instead of hanging until the subscribe timeout.

## 6. Subscribe = ready, and only what comes after

`good.EventClient.subscribe` **blocks** until the poll thread stands at the end of the topic: after it returns,
the test may act at once; nothing published before is delivered (no old messages from earlier runs), nothing
published after is missed. The listener is registered **before** positioning, so there is no window in which
an event is read with nobody to give it to.

`bad.EventClient.subscribe` returns at once and positions "on the next round": an event published right after
`subscribe` is behind the start position and lost. Tests compensate with `sleep(1000)` after subscribe,
which is the problem of §2 again.

## 7. How it looks with a real Kafka

`good.EventClient` is the same design as a Kafka test client; the fake replaces only the broker. With
`kafka-clients` (stage 4: real broker on Testcontainers; full code and integration checklist in
[docs/stage4/kafka-event-client.md](../../../../../../../../../../docs/stage4/kafka-event-client.md)):

| Fake | Kafka | Why |
|---|---|---|
| positions in a `HashMap`, touched only by the poll thread | **every** `KafkaConsumer` call only from `event-client-poll`; other threads send `Runnable`s through a `BlockingQueue` | `KafkaConsumer` is not thread-safe (it throws `ConcurrentModificationException` on concurrent use) |
| `positions.computeIfAbsent(topic, broker::endOffset)` | `assign(all partitions)` + `seekToEnd(new ones)` + `position(tp)` for each new one | `assign` instead of `subscribe` + `group.id`: no rebalances, no committed offsets shared between runs. `seekToEnd` is **lazy**: without `position()` the end is computed at the first `poll()`, after a message the test may already have triggered |
| keep existing positions when a second topic comes | `assign()` replaces the whole assignment; pass old + new partitions, seek only the new ones | subscribing to `payments` must not lose or re-read `orders` |
| unknown topic → `IllegalArgumentException` | `partitionsFor(topic)` empty or null → fail with the topic name | with `auto.create.topics.enable=true` the broker may create it instead: decide which failure you want |
| `poll` idles on the commands queue | `poll()` with no assignment throws `IllegalStateException`: wait for the first command instead | |

Sections 1–6 do not change at all: they are about threads, not about Kafka.

## Pros / cons

| Pros | Cons |
|---|---|
| Tests as fast as the SUT: no fixed sleeps | A background thread in test infrastructure: one more thing to close (`AutoCloseable`, extension) |
| Noise in shared topics does not matter: match on the test's own id | Predicates need an id the test controls; the SUT must expose one (key, correlation header) |
| `subscribe` = ready: no "warm-up" sleeps, no old messages | `subscribe` blocks; one per test class (or an extension) keeps it cheap |
| A failing listener or a dead thread is loud and local | Swallowed listener exceptions must be logged visibly, or an assertion inside a listener "passes" silently |
| `SwitchableListener` makes cleanup safe | `synchronized` makes `deactivate` wait for a slow delegate |

## When NOT to apply

- **The SUT can be tested synchronously.** If the code under test is yours, test the handler directly with the
  message as input; the broker is for a few integration/E2E checks.
- **Do not assert inside the listener.** An `AssertionError` is an `Error`, not a `RuntimeException`: it gets
  past the per-listener catch and ends the poll loop of either client (the good one records it, so the next
  `subscribe` fails loudly). The test that asserted never sees it. Collect in the listener, assert in the test.
- **Do not build this for one topic read once.** A short-lived consumer per test, positioned at the end, is
  simpler if the suite is small; the shared client pays off with many tests and topics.
- **No `SwitchableListener` if the listener touches nothing that is released**: `received::add` on a queue that
  lives as long as the test needs only `unsubscribe`.
- **Not for counting "exactly N events, nothing more".** Proving absence needs a wait to the end of a window;
  use a quiet period (`poll` returns `null` for X ms) and say so in the test name.

## Try it yourself

1. Add an `await(BlockingQueue, Predicate, Duration)` that fails with an `AssertionError` listing the **skipped**
   events' keys. Why does that message save more debugging time than `Optional.empty()`?
2. Remove `listeners.computeIfAbsent(..).add(listener)` from the start of `good.EventClient.subscribe` and put it
   after `ready.get(..)`. Write a test that shows the lost event (hint: the poll thread must read between
   positioning and registration; a slow first listener or a latch in a test subclass helps).
3. Make `bad.EventClient` correct step by step, running `BadKafkaEventsTest` after each: which characterization
   test flips first? Which fix makes two tests flip at once?
4. A test listens to `orders` and `payments` and must check that `PaymentReserved` comes **after**
   `OrderCreated` for the same order. One queue or two? What does `Event.offset()` tell you across topics
   (nothing) and what does the event timestamp tell you (less than you think)?
5. Smell check on your framework: `grep -rn 'Thread.sleep\|new ArrayList.*listener\|::add)\|unsubscribe(.*::'`.
   For each hit decide: queue, `pollMatching`, `EventSubscription`, or leave it.
