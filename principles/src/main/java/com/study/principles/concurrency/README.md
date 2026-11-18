# Concurrency in tests: when a test needs more than one thread

Asynchrony is not a goal but a consequence. A test runs on more than one thread when the SUT answers later
(events, eventual consistency), when the test does independent work at once (data setup), when the test
must create a race on purpose (double spend), or when the code under test returns a future. Each case has
its own tool, and the commonest mistake is using the wrong one: `CompletableFuture` where a poll is needed,
`sleep` where an event can be waited for.

Parallel **test execution** (what tests may share) is a different axis: see `principles/parallel`.
Here the threads live **inside one test**.

| Package | Scenario | Main tool | Status |
|---|---|---|---|
| [`events`](events/README.md) | subscribe **before** the action, wait for *my* event: Kafka, WebSocket, webhook, UI | thread-safe buffer + `EventWaiter.pollMatching`, subscription = readiness | Kafka done; WebSocket, webhook, UI next |
| `fanout` | independent preconditions created at once | `CompletableFuture.allOf` on an own executor, deadline, one failed branch | planned |
| `race` | race conditions **in the SUT**: double spend, idempotency key, lost update | start gate (`CountDownLatch`) + N concurrent calls | planned |
| `eventual` | "it will show up": read models, search index, mailbox | poll with a deadline: hand-rolled vs Awaitility (never `CompletableFuture` + `sleep`) | planned |
| `futures` | the SUT itself returns `CompletableFuture` | injected `Executor`, never-completing fake, timeout and cancel paths | planned |

## Which tool, at a glance

| The test needs to... | Use | Not |
|---|---|---|
| react to something the SUT **pushes** | subscription before the action + `pollMatching` | `sleep`, then look |
| see a state that **appears eventually**, with no push | poll with a deadline (Awaitility) | `supplyAsync` + `sleep` loop |
| do independent slow things **at once** | `CompletableFuture.allOf` on an own pool | the common `ForkJoinPool` |
| make N calls hit the SUT **at the same moment** | latch gate + pool of N threads | a loop of `supplyAsync` without a gate |
| run tests **in parallel** | JUnit parallel execution (`principles/parallel`) | futures inside one test |

Rules that hold in every package:

- **Every wait has a deadline.** `get(timeout)`, `orTimeout`, `poll(timeout)`; never a bare `join()` or `get()`.
- **Own executor, closed after the test.** The common pool is shared by the whole JVM and is small.
- **Assert in the test thread.** An assertion inside a callback fails on a thread nobody watches.
- **Deterministic tests of nondeterminism.** Bad/good tests script interleavings with latches; a test that
  needs a lucky schedule is a flaky test, not a kata.

Java 17 here: no virtual threads. With Java 21 `fanout` and `race` get cheaper (a thread per call),
the rules above do not change.
