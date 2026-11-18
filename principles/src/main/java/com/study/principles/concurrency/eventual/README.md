# Eventual consistency: wait for a condition, with a deadline, on the right thread

The write is accepted at once (201), and the read side shows it **later**: a CQRS projection, a search index, a
cache, a replica, a mailbox. Nobody pushes anything to the test, so there is no event to subscribe to (that is
`events`). The test has to **ask again** until the state is there. Asking again is easy to get wrong in five
ways: not at all, for a fixed time, forever, for the wrong condition, or on the wrong thread.

Part of [`concurrency`](../README.md). `CompletableFuture` has no place here: there is nothing to complete. A
`supplyAsync` with a `sleep` loop inside is a poll with extra threads.

| Package | What to look at |
|---|---|
| `eventual` | `FakeOrderSystem` (the SUT: commands accepted at once, the read model updated by a projector thread after a lag; paying ships at once, so `PAID` may never be visible; `breakReads` = an expired token; `reads()` counts the load), `OrderView` (statuses only move forward), `ReadErrors` (404 "not yet", 401 "never"), `ThreadBoundClient` (usable only on its creating thread, like a Playwright `Page`), `EventualDemo` |
| `bad` | `OrderChecks`: `waitForOrder` without a deadline, catching every exception; `waitForStatus` for an exact intermediate state |
| `good` | `Poll` (a hand-rolled poll done right: deadline, only named exceptions are "not yet", returns the matched value, a message with the last value or error, runs on the caller's thread), `OrderChecks` on top of it (`statusAtLeast`) |
| tests | `BadEventualTest`, `GoodEventualTest` (on `Poll`), `AwaitilityEventualTest` (the same with Awaitility, a test-scoped dependency) |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.concurrency.eventual.EventualDemo
mvn -q -pl principles test -Dtest='com.study.principles.concurrency.eventual.**.*Test'
```

`EventualDemo` (projection lag 50–400 ms, 20 runs):
```
assert right after the write        0/20 ok, avg    0 ms,  1.0 reads per test
sleep(200), then read               6/20 ok, avg  203 ms,  1.0 reads per test
sleep(500), then read              20/20 ok, avg  503 ms,  1.0 reads per test
Poll: every 50 ms, at most 5 s     20/20 ok, avg  274 ms,  6.2 reads per test
```
A sleep long enough to pass is the **worst-case lag paid on every run**, and only until the stand gets slower. A
poll costs the **actual lag plus up to one interval**, and a few cheap reads.

## 1. Read-your-writes is not a given

`placeOrder` returns, `getOrder` answers 404 (`assertRightAfterTheWrite_theReadSideHasNotCaughtUp`). The SUT is not
broken: it never promised the read side would be up to date at once. Find out which reads in the SUT are eventually
consistent (search, lists, reports, anything behind a queue or a cache) and wait only for those. Waiting
"everywhere, just in case" hides real read-your-writes bugs where the product **does** promise them.

## 2. A poll needs four things

| | bad | good |
|---|---|---|
| **deadline** | `while (true)` | `atMost(5 s)`; the timeout is a bound, the poll returns as soon as the condition holds |
| **which errors mean "not yet"** | `catch (Exception)`: a 401 spins forever (`noDeadlineAndCatchAll_a401SpinsUntilSomebodyKillsTheThread`) | only 404; anything else fails **at once** with its own message (`unexpectedError_failsAtOnce_notAfterTheTimeout`) |
| **a failure message** | "timed out" / "job cancelled" | what it waited for, how long, how many reads, the **last value or error** (`timeout_saysWhatItWaitedFor_andTheLastError`) |
| **pacing** | `sleep(1)` hammers the SUT, `sleep(1000)` wastes up to a second | an interval that matches the lag (50–200 ms for most APIs); `reads()` shows the load (`theIntervalSetsTheLoadOnTheSut`) |

Plus: **assert on the value the poll returned**, not on a fresh read after it. The state may have moved on.

## 3. Wait for a condition that stays true

`waitForStatus(id, PAID)` fails while the system did everything right: payment and shipping were projected
together, and the read side went from `NEW` straight to `SHIPPED`
(`waitingForAnExactIntermediateState_failsWhenTheReadSideJumpsPastIt`). Even when the intermediate state is
visible, it may be visible for less than one interval.

- Statuses that only move forward: wait for **"at least PAID"** (`OrderView.Status.isAtLeast`), then assert on what
  you got.
- When the intermediate state **is** the requirement ("the order was PAID before it was SHIPPED"), the read
  model's current state is the wrong source: assert on the history (an audit log, the events, `events/kafka`).

## 4. Absence needs a window

"Bob's order never shows up in Alice's search" cannot be checked at one instant: right after the write, absence
looks exactly like "not projected yet". Hold the condition for a window longer than the lag: Awaitility's
`during(500 ms)` (`during_provesAbsenceOverAWindow_notAtOneInstant`), and check that the window covered the lag
(bob's order **is** in bob's search afterwards). Name such tests honestly: "within 500 ms", not "never".

## 5. Awaitility: two settings that matter

Awaitility gives everything from §2 for free, plus `untilAsserted` (AssertJ inside the condition, its message in
the failure), `until(supplier, predicate)` returning the value, `alias` for the message, `during` for windows,
`pollDelay`, Fibonacci/iterative intervals. Two defaults to know:

- **Exceptions.** Not ignored by default: an exception from the condition is rethrown **as is, at once** (here the
  401 after one read, `anExceptionThatIsNotIgnored_failsAtOnce`). `ignoreException(NotFound.class)` names "not yet".
  `ignoreExceptions()` turns every failure, the 401 included, into a `ConditionTimeoutException` that does not
  mention it (`awaitilityIgnoringAllExceptions_turnsA401IntoATimeout`).
- **Threads.** By default the condition runs on Awaitility's own thread (`awaitility-thread`), not the test's. A
  thread-bound client (a Playwright `Page`, a client keeping per-test auth or a request log in a `ThreadLocal`, an
  open JDBC transaction) breaks or silently loses its context
  (`awaitilityByDefault_evaluatesOnItsOwnThread_aThreadBoundClientBreaks`). `pollInSameThread()` fixes it; the
  price is that a condition that blocks cannot be interrupted at the deadline. `Poll` here always runs on the
  caller's thread.

Hand-rolled or Awaitility? Awaitility, unless the project cannot take the dependency, or the poll lives in `main`
code of a framework that should not depend on a test library (as `good.Poll` here: Awaitility is test-scoped in
this module). Either way, check it against the table in §2.

## Pros / cons

| Pros | Cons |
|---|---|
| Tests as fast as the real lag, not the worst case | Every read is a request: the interval is a load decision |
| Failures that say what was seen last: "status NEW", not "timeout" | A long `atMost` makes a real failure slow to report: size it to the SLA, not "to be safe" |
| Monotonic conditions make waits stable across interleavings | Needs a monotonic signal or a history; not every API has one |
| Awaitility: one line per wait, consistent messages | Its own thread by default: thread-bound clients need `pollInSameThread` |

## When NOT to apply

- **The SUT pushes an event for it.** Subscribe before the action and wait for the event (`events`): no polling
  load, no interval to tune.
- **The read is promised to be consistent.** Polling where the product guarantees read-your-writes hides the very
  bug you should catch. Assert at once.
- **Do not poll to "stabilize" flaky tests.** A poll around an assertion that fails for another reason (wrong
  data, a race in the test) only makes the failure slower.
- **Not for absence without a window**, and not for "exactly N" counts unless the count is monotonic and you wait
  for "at least N" plus a quiet window.

## Try it yourself

1. Add `pollDelay` to `Poll` (a first read only after a delay). When does it save reads, and when does it only add
   latency? Then measure Awaitility's default first-read delay with `system.reads()` and a timestamp, rather than
   trusting a blog post.
2. Make `Poll` back off (50, 100, 200 ms...). Write the test that shows fewer reads for a long lag, and the one that
   shows the worse latency for a short one.
3. Add `system.search("alice")` with a **cache** in front of it (a 1-second TTL). Which poll interval still helps,
   and what does the test need to bypass instead?
4. Rewrite `waitingForAnExactIntermediateState...` to prove "PAID happened before SHIPPED" correctly. What does the
   SUT have to expose for that, and why can't the read model answer it?
5. Smell check on your framework: `grep -rn 'while (true)\|catch (Exception\|ignoreExceptions()\|Thread.sleep' --include=*.java`.
   For each poll: deadline, "not yet" errors, message, interval, thread?
