# Race tests: make the calls really collide, assert across all of them

Some of the most expensive bugs only exist **between** requests: two withdrawals that both see the old balance
(double spend), two deposits where one overwrites the other (lost update), a retry with the same idempotency key
that arrives while the first request is still running (double charge). A sequential test cannot see them. A
"concurrent" test sees them only if its calls **actually overlap**, and only if it checks the invariant
**across all calls**, not call by call.

The kata has two axes:

| | racy SUT | safe SUT |
|---|---|---|
| **bad harness** | the bug is hidden or understated | passes, but would pass on the racy one too |
| **good harness** | the bug is **found** | **passes** |

A harness is worth something only in the bottom row: it must tell the two versions of the SUT apart.

| Package | What to look at |
|---|---|
| `race` | `Wallet` (`withdraw`: 200 / 409, `deposit`) with `RacyWallet` (read → window → write) and `SafeWallet` (conditional update); `Payments` (`charge(idempotencyKey)`) with `RacyPayments` (check, then charge) and `IdempotentPayments` (claim the key first); `InFlight` (how many calls overlapped); `RaceDemo` |
| `bad` | `ConcurrentCalls`: the framework's shared executor decides the concurrency, no start gate, `join()` in order without a timeout, a `CyclicBarrier` larger than the pool |
| `good` | `StartingGate.runTogether(n, call, timeout)`: own pool of N, ready/start latches, every outcome (value or exception) collected against one deadline, pool always shut down |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.concurrency.race.RaceDemo
mvn -q -pl principles test -Dtest='com.study.principles.concurrency.race.**.*Test'
```

`RaceDemo` (the SUT has a 20 ms window between its read and its write):
```
10 x withdraw(100) from 100        racy wallet            safe wallet
bad: shared pool of 1               1 paid, overlap  1     1 paid, overlap  1
bad: shared pool of 2               2 paid, overlap  2     1 paid, overlap  2
good: starting gate, 10 threads    10 paid, overlap 10     1 paid, overlap 10

10 x charge("key-1")               racy payments          idempotent payments
good: starting gate, 10 threads    10 charges made         1 charges made
```
With a pool of 1 the racy wallet **passes**: "10 concurrent withdrawals" ran one after another. With a pool of 2
the bug shows as "2 paid", a number set by the pool size, not by the SUT. Only the starting gate gives every
call the chance to collide.

## 1. Did the calls overlap? Measure it

Before trusting a green race test, answer one question: **how many calls were inside the SUT at the same
time?** Here `InFlight.max()` counts it; against a real system use timestamps from access logs or the
DB, or at least the start times of the calls on the client side.

What decides the overlap:

- **The pool.** `supplyAsync(call, frameworkExecutor)` gives the concurrency of that executor: 1 → sequential
  (`sharedPoolOfOne_..._theRacyWalletPasses`), 2 → rounds of 2. Without an executor, the common `ForkJoinPool`
  gives "number of cores minus one", which differs between a laptop and a CI agent. The test must own a pool of
  **exactly N** threads.
- **Start-up stagger.** Threads start one by one; the first may finish before the last starts. A gate fixes it:
  every party counts down `ready` and waits on `start`; the harness opens `start` only once **all N** are
  at the gate. (`allPartiesAreInsideTheSutAtOnce`: overlap = 10.)
- **The window.** Real windows are short (a DB round trip, a call to a fraud service). The gate makes the calls
  arrive within microseconds of each other, which is what gives a short window a chance.

`CyclicBarrier(n)` on a pool smaller than `n` waits for parties that cannot start, forever
(`cyclicBarrierForTenOnAPoolOfFour_waitsForever`). The barrier is not wrong; a barrier without a timeout, on a pool
the test does not control, is.

## 2. Assert across calls, in the test thread

Each call alone sees a valid answer: 200 or 409. The bug is in the **combination**: ten 200s from a balance of
100. So:

- **Collect every outcome**, value or exception, then assert the **invariant** over all of them: exactly one 200,
  the rest 409, balance 0; ten deposits of 10 → +100; one idempotency key → one charge, and everybody got that
  charge.
- **Per-call assertions are blind to it.** `statusCode(anyOf(200, 409))` inside each call passes on the racy wallet
  (`perCallAssertions_everyCallLooksFine_theInvariantAcrossCallsIsNeverChecked`).
- **`statusCode(200)` inside each call is wrong for a correct SUT**: nine 409s are the expected answer. And with
  `join()` in order, the first failure throws and the test never sees the full list
  (`statusAssertionInsideTheCall_firstFailureThrows_andHidesWhatTheOtherCallsGot`).
- **Check the final state and the answers.** After the double spend the balance is 0, which looks right; the
  money is in the nine extra 200s.

`StartingGate` returns `Outcome(party, value, error)` for every party; a failing or hung call is one outcome among
the others (`oneFailingCall_doesNotHideTheOthers`, `aHungCall_isReportedByNumber_andTheTestDoesNotHang`).

## 3. What to race, and what correct looks like

| Bug | Racy code | Fix in the SUT | The test's invariant |
|---|---|---|---|
| double spend | `SELECT balance` → check → `UPDATE SET balance = ?` | `UPDATE ... SET balance = balance - ? WHERE balance >= ?` (or `SELECT ... FOR UPDATE`, or a version column) | at most one 200 per balance, the rest 409, balance never negative |
| lost update | read → compute → write back | atomic increment, or optimistic locking (409 on a stale version, the client retries) | sum of accepted deposits = balance change; every rejected one is reported |
| double charge | "key seen?" → charge → store the key | claim the key first: unique insert / `putIfAbsent` of an in-flight marker | one key → one charge, every response carries the same charge id (or 409 "in progress") |
| over-deduplication | a key derived from the body, a too-wide lock | key = the client's idempotency key only | N different keys → N charges (`idempotentPayments_differentKeys_areNotMergedByMistake`) |

## 4. A green race test is evidence, not proof

A race test finds a bug **with some probability**. Raise it, and be honest about it:

- **Repeat**: many rounds with fresh data (`safeWallet_exactlyOneWithdrawalSucceeds_roundAfterRound`, 20 rounds).
  One red round is a found bug; twenty green ones are a measured confidence.
- **More parties** than "2" and windows the SUT really has: a race behind a cache or a fast path needs a payload
  that reaches the slow path.
- **Name it**: "no double spend in 20 rounds of 10 concurrent withdrawals" is a claim a reviewer can judge;
  "concurrency works" is not.
- In the kata, the fake's 20 ms window makes the bad and good tests deterministic. Against a real stand
  the same harness gives a probability; keep these tests in a separate tag, not in the PR gate.

## Pros / cons

| Pros | Cons |
|---|---|
| Finds bugs no functional test can: double spend, lost update, double charge | Probabilistic against a real system: a pass is evidence, not proof |
| `StartingGate` is small, JDK-only, reusable for any call | N threads per round: costly with large N or many rounds |
| Measuring overlap turns "it's concurrent, trust me" into a number | Needs data isolation per round (fresh account, fresh key) |
| Invariants over all outcomes say what the business rule is | Needs an observable for the invariant (balance, charges made, an audit log) |

## When NOT to apply

- **The SUT's concurrency control is a unit you own.** Test the lock / the conditional update at the unit level,
  with a deterministic interleaving (latches in the code, as in the `events/kafka` kata); keep the API race test as
  one smoke check.
- **Load testing.** Ten colliding calls are not a performance test; use a load tool (Gatling, k6) for throughput
  and latency.
- **Shared, long-lived data.** Racing on an account other tests also use gives other tests' failures. One fresh
  entity per round.
- **No observable invariant.** If you cannot tell from the outside whether money was taken twice, the race test
  cannot either: ask for the observable (an audit endpoint, a charges list) first.

## Try it yourself

1. Change `RacyWallet`'s window to 0 and run `racyWallet_doubleSpendIsFound` 50 times
   (`@RepeatedTest(50)`). How often does it still find the bug? What does that say about real windows?
2. Add a `VersionedWallet`: `withdraw` reads `(balance, version)` and writes only if the version did not change,
   otherwise 409 "conflict". Write the invariant test: what is the allowed set of outcomes now, and who retries?
3. Make `StartingGate` record each party's start time (just after `start.await()`), and report the spread. What
   spread do you get with the gate, and with a plain `for` loop of `submit`?
4. `IdempotentPayments` waits for the in-flight request. Change it to answer 409 "in progress" instead (as Stripe
   does). How does the test's invariant change, and what must a client do on that 409?
5. Smell check on your framework: `grep -rn 'parallel()\|supplyAsync(\|CyclicBarrier\|statusCode(200)' --include=*.java`
   in tests that claim to test concurrency. For each: who sets the concurrency, is there a gate, where is the invariant?
