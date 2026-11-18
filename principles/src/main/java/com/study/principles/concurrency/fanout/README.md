# Fan-out: independent preconditions at once, and what happens when one fails

A test needs a user, a product, stock and a promo code. Four independent `POST`s of 200 ms each: 800 ms in a row,
200 ms at once. `CompletableFuture.supplyAsync` + `allOf` gets the happy path in five lines. The kata is about
the other paths: whose threads run the branches, what the branches do **not** inherit from the test, what happens
to the data the other branches created when one fails, and how long the test waits for a stuck dependency.

Part of [`concurrency`](../README.md). Fan-out is the only scenario here where the **test** starts the
concurrency. In `events`, `eventual` and `race` the concurrency is the SUT's, or the test provokes it on purpose.

| Package | What to look at |
|---|---|
| `fanout` | `FakeShopApi` (slow independent `create(type)`, optional auth read from `TestContext` on the calling thread, `failOn` / `hangOn`, `live()` = what is left behind), `TestContext` (per-test token in a `ThreadLocal`, `wrap` to carry it), `Cleanup` (undo registry, LIFO, late registrations run at once), `Fixture`, `FanoutDemo` |
| `bad` | `Preconditions`: `supplyAsync` without an executor, no context, cleanup registered after `allOf` succeeded, `join()` without a deadline |
| `good` | `Preconditions(api, executor, cleanup, timeout)`: the test's executor, `TestContext.wrap` per branch, cleanup registered by each branch, one deadline, every failure reported |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.concurrency.fanout.FanoutDemo
mvn -q -pl principles test -Dtest='com.study.principles.concurrency.fanout.**.*Test'
```

`FanoutDemo`:
```
sequential, 4 x 200 ms                       828 ms
bad: supplyAsync on the common pool          208 ms
good: own pool, deadline                     209 ms
promo fails: entities left behind            bad 3, good 0
stock stuck for 2 s, bad                     returned after 2005 ms
stock stuck for 2 s, good (deadline 500 ms)  failed after 509 ms: Preconditions not ready within PT0.5S: still waiting for [stock]
```
On the happy path the two are equally fast. Everything that tells them apart is on the other paths.

## 1. Whose threads

`supplyAsync(task)` without an executor runs on `ForkJoinPool.commonPool()`: sized by the machine (cores − 1),
shared with every parallel stream and every other `supplyAsync` in the JVM, so with every parallel test. The test
can neither size it nor stop it.

Pass an executor the **test lifecycle owns**: created by an extension (or the test), sized for the fan-out,
`shutdownNow()` after the test. The last part matters more than it looks: `CompletableFuture.cancel(true)` does
**not** interrupt a running `supplyAsync` task (the future does not know its thread). A branch the test gave up on
keeps running until whoever owns the pool interrupts it.

## 2. What branches do not inherit

Per-test state in a `ThreadLocal` (the auth token an extension set, the test id in SLF4J's `MDC` for log
correlation, the report's current step) belongs to the **test thread**. A pool thread has its own value: none
(`theTestsContextStaysOnTheTestThread_everyBranchGets401`), or worse, whatever the previous task on that thread left
there, which is how one test's requests end up in another test's log.

`TestContext.wrap(task)` captures the context **when the branch is created** (on the test thread), sets it on the
worker for the duration of the task, and **restores** the worker's previous value afterwards
(`theContextDoesNotLeakIntoTheNextTaskOnAReusedPoolThread`). For MDC: `MDC.getCopyOfContextMap()` / `setContextMap`
in the same shape. Better still: pass the context explicitly (a `Session`, a client already configured with the
token), see the parallel kata, mechanism 4.

## 3. One branch fails

`allOf(...)` completes exceptionally with the **first** failure; `join()` throws it. Meanwhile the other branches
have created their entities, and the bad version registers cleanup only after **all** succeeded: three entities
leak on every such failure (`oneBranchFails_whatTheOthersCreatedIsNeverCleanedUp`), and on a shared stand they pile up.

The good version:
- each branch **registers its own undo** as soon as its entity exists, from its own thread (`Cleanup` is
  thread-safe);
- on failure, it reports **every** failed branch, with the causes as suppressed exceptions
  (`failedBranches_areAllReported_andEverythingCreatedIsCleanedUp`);
- a branch that finishes **after** the test has already run its cleanup registers late, and `Cleanup` runs a late
  undo at once (`aBranchThatFinishesAfterTheTestGaveUp_isStillCleanedUp`).

## 4. One deadline for the fan-out

`allOf(...).get(timeout)`, and on timeout an error that names the branches still pending
(`stuckDependency_failsAtTheDeadline_namingIt`). Without it the test takes as long as the slowest dependency is
stuck, and the CI job's timeout reports nothing useful.

## Pros / cons

| Pros | Cons |
|---|---|
| Setup time = the slowest precondition, not the sum | More load on the stand at once; a rate-limited API may answer 429 |
| Own executor: sized, stoppable, no interference with other tests | One more resource per test (or per class) to create and shut down |
| Cleanup per branch: no leaks on partial failure | Undo must be safe for entities the test never saw (it failed before using them) |
| Every failure reported, stuck branches named | Context propagation must be explicit, and kept up to date with new `ThreadLocal`s |

## When NOT to apply

- **The preconditions depend on each other.** An order needs the user's id: chain (`thenCompose`) or do it
  sequentially; a fan-out of dependent calls only adds races.
- **One bulk call exists.** A seed endpoint, a SQL fixture, or a snapshot of the DB beats four parallel calls.
- **Fast calls.** For four 10 ms calls the threads, context copying and error handling cost more than they save.
- **Not to parallelize tests.** Running tests in parallel is the runner's job (`principles/parallel`), not
  `supplyAsync` inside a test.

## Try it yourself

1. Add a fifth precondition that depends on two others (a cart needs the user and the product). Express it with
   `thenCombine` so it starts as soon as both are ready. How does the error reporting in `good.Preconditions` change?
2. Make `TestContext` carry two values (token and test id). Where does `wrap` need to change, and how would you make
   forgetting one impossible?
3. Make one undo in `Cleanup` throw. Check that the other undos still run and that the report lists the failure.
4. Size the pool to 2 for 4 branches. What happens to the timing, and does the deadline still name the right
   branches as pending?
5. Smell check on your framework: `grep -rn 'supplyAsync(\|runAsync(\|allOf(' --include=*.java` in test code.
   For each: whose executor, which `ThreadLocal`s the branch needs, where cleanup is registered, what the deadline is.
