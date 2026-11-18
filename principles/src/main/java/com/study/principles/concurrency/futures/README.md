# Futures: testing code that returns `CompletableFuture`

Here the SUT itself is asynchronous: a service that asks three price providers at once and returns the cheapest
quote as a `CompletableFuture`. Testing it has its own traps: an assertion inside `thenAccept` that nobody hears,
a `join()` that hangs the build, a `sleep` that tests the scheduler, a real timeout every test has to wait for. The
cure is mostly on the SUT's side: **no threads of its own, time injected, every outcome a value**. Then the test
owns every future and the clock, and most async tests run on one thread, in a known order, in microseconds.

Part of [`concurrency`](../README.md). This is the unit/component level: in E2E tests through an API the
future is behind HTTP, and you are back in `eventual` or `events`.

| Package | What to look at |
|---|---|
| `futures` | `PriceProvider` (async client: `CompletableFuture<Quote>`), `Quote`, `BestQuote` (best + why others were skipped), `NoQuotesException`, `Timer` (`after(delay)`; `Timer.system()` for production) |
| `bad` | `QuoteAggregator`: a pool per instance never shut down, `supplyAsync(() -> provider.quote().join())`, a hard-coded 500 ms real timeout, `exceptionally(e -> null)` |
| `good` | `QuoteAggregator(providers, timeout, timer)`: composes the providers' futures, no threads, `applyToEither` with the injected timer, every attempt a value |
| tests | `ManualTimer` (fire every timeout now), `Providers` (answered / failed / late / never), `BadFuturesTest`, `GoodFuturesTest` |

Run:
```
mvn -q -pl principles test -Dtest='com.study.principles.concurrency.futures.**.*Test'
```

## 1. Bad tests of async code

| Smell | What happens | Instead |
|---|---|---|
| assert inside `thenAccept` / `whenComplete` | the `AssertionError` completes the **returned** future exceptionally; nobody joins it; the test is green (`assertionInsideThenAccept_isSwallowed_andTheTestIsGreen`) | get the value out, assert in the test thread: `assertThat(future).succeedsWithin(d).satisfies(..)` |
| `join()` / `get()` without a timeout | a future that never completes hangs the build (`joinWithoutATimeout_hangsWhenAProviderNeverAnswers`) | `succeedsWithin`, `failsWithin`, `get(timeout)`; or, better, a future already complete because the test completed its inputs |
| `sleep(50)` then `isDone()` | tests the machine's scheduler: flaky either way (`sleepThenIsDone_checksTheSchedulerNotTheCode`) | control completion, or wait with a deadline |
| a real timeout in the timeout test | every test of that path waits for it; with a production value (2–30 s), nobody writes those tests | inject the time (`Timer`), fire it by hand |

AssertJ has the vocabulary: `isCompletedWithValue(Matching)`, `isNotDone`, `isCompletedExceptionally`,
`succeedsWithin(Duration)` (returns an assertion on the value), `failsWithin(Duration)`.

## 2. A SUT that can be tested

**No threads of its own.** `good.QuoteAggregator` only composes futures (`handle`, `applyToEither`, `allOf`,
`thenApply`). Continuations run on the thread that completes the input: the HTTP client's, the timer's, or **the
test's**, when the test calls `complete(..)`. So
`theResultWaitsForEveryProvider_whateverOrderTheyAnswerIn` is single-threaded and deterministic: complete C, then B,
check "not done", complete A, check the result.

The bad aggregator does `supplyAsync(() -> provider.quote(sku).join(), pool)`: an async call turned into a blocking
one on a pool thread. It creates a pool per instance and never shuts it down
(`aPoolPerInstance_neverShutDown_leaksThreads`), and when a provider never answers, `orTimeout` completes the
outer future while the pool thread stays blocked in `join()` forever. Enough such calls and the pool is exhausted:
healthy providers start timing out too. (With the default thread factory those threads are not daemons: the JVM
does not exit.)

**Time injected.** `Timer.after(timeout)` is a future; production uses `Timer.system()`
(`CompletableFuture.delayedExecutor`), tests use `ManualTimer`. "Two seconds pass" becomes `timer.fireAll()`
(`aProviderThatNeverAnswers_isSkippedAtTheTimeout_testedWithoutWaitingForIt`: < 100 ms for a 2 s timeout). The same
move as injecting a `Clock` (`principles/time`) and installing the page clock (`events/ui`). One test keeps the real
timer honest (`theSystemTimer_reallyWaits`).

**Every outcome a value.** Each provider's attempt ends as a quote or a reason (`handle`), so one failure does not
lose the others, and the reasons reach the result (`BestQuote.skipped`) or the final exception
(`noProviderAnswers_theFutureFailsWithEveryReason`). `exceptionally(e -> null)` keeps the first property and loses
the second. A client that **throws** instead of returning a failed future is a real thing; it is caught too
(`aClientThatThrowsInsteadOfFailingTheFuture_isSkippedToo`).

## 3. Unwrapping

`supplyAsync` stores a task's exception wrapped in `CompletionException`; `get()` wraps the cause in
`ExecutionException`; `join()` throws `CompletionException`. Assert on the **cause**:
`assertThatThrownBy(() -> f.get(1, SECONDS)).isInstanceOf(ExecutionException.class).cause().isInstanceOf(..)`, and
unwrap in the SUT before building messages (`describe` in `good.QuoteAggregator`).

## Pros / cons

| Pros | Cons |
|---|---|
| Async logic tested on one thread, in a known order, without waiting | The SUT must be designed for it: injected time, no hidden pools |
| Timeout paths tested in microseconds | A manual timer does not check the real timer's wiring: keep one real-time test |
| Failures carry their reasons to the result | Composition code (`handle`, `applyToEither`) is denser than blocking code |
| No thread leaks: the aggregator owns no threads | Continuations run on "some" thread: code in them must be fast and thread-safe |

## When NOT to apply

- **The async part is a thin wrapper.** If `bestQuote` were `supplyAsync(() -> syncBestQuote(sku))`, test the
  synchronous core directly and keep one async smoke test.
- **Do not make code async to test it.** If the production code blocks anyway, a `CompletableFuture` API only adds
  the traps above.
- **Not for E2E.** Through HTTP there is no future to complete: wait for state (`eventual`) or events (`events`).
- **`ManualTimer` everywhere** hides wiring bugs (a timer that is never started, a wrong unit). One test with the
  real timer per component.

## Try it yourself

1. Give `ManualTimer` real durations (`advance(Duration)` fires only timers due by then) and give provider C a 1 s
   timeout while A and B have 2 s. Test that C is skipped at 1 s and the result waits for A and B.
2. Add cancellation: when the result is cancelled, cancel the providers' futures. Write the test with futures the
   test owns: what can it observe?
3. Make the bad aggregator's pool starvation visible: four calls with a provider that never answers, then a call
   with healthy providers only. What does it return, and after how long?
4. Replace `applyToEither` with `orTimeout(timeout)`. Which test can no longer run without waiting, and why is
   `orTimeout`'s scheduler not injectable?
5. Smell check on your codebase: `grep -rn 'thenAccept(.*assert\|\.join()\|supplyAsync(() -> .*\.join()' --include=*.java`.
