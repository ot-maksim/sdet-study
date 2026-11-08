# Invariant protection on a value object: retry policy

An immutable value object protects its invariants in one place, the constructor. After that, every
client can trust it: no guards, no re-validation, no defensive arithmetic. The bad version is the
usual "private fields + getter/setter per field". It looks encapsulated but guarantees nothing.

Invariants:
1. `maxAttempts >= 1`;
2. `0 < initialDelay <= maxDelay`;
3. `retryOn` is non-empty, holds only error codes 400..599 and cannot change behind the policy's back.

| Class | What to look at |
|---|---|
| `bad.RetryPolicy` | setters, `long ...Ms`, live set from the getter, caller's set stored as is |
| `bad.Retrier` | rules live in the client; a "status" of `-1` when nothing was sent |
| `good.RetryPolicy` | record + compact constructor: fail fast, `Set.copyOf`, `Duration`, `with...` copies re-validated |
| `good.Retrier` | the same client without a single guard: the invariants make it unnecessary |
| `FlakyEndpoint`, `Sleeper` | fakes: scripted HTTP statuses and recorded (not real) pauses |
| `RetryDemo` | a shared policy tweaked by one "test" breaks the next one; invalid policies rejected |
| `BadRetryPolicyTest` (test) | every corruption of the bad version, as passing tests |
| `GoodRetryPolicyTest` (test) | invalid policies cannot be built, nothing leaks, client behaviour |
| `RetryPolicyPropertyTest` (test) | jqwik: for any valid policy and any responses, 1..maxAttempts requests and a bounded, non-decreasing backoff |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.invariants.retry.RetryDemo
mvn -q -pl principles test -Dtest='com.study.principles.invariants.retry.*Test'
```

## Techniques used

| Way an invariant breaks | Protection here |
|---|---|
| object created invalid | checks in the compact constructor, fail fast with a precise message |
| field changed alone | no setters; `withMaxAttempts(..)` builds a new policy through the same constructor |
| leaked mutable internals (input and output) | `Set.copyOf`: detached from the caller, unmodifiable, null-hostile |
| wrong unit | `Duration` instead of `long ms` |
| rules duplicated in clients | `retries(status)` and `delayBefore(attempt)` live on the policy |

Preconditions and invariants are different things. `delayBefore(attempt)` checks its **argument**
(a precondition) and trusts its **fields** (the invariant, checked once at construction).

## Pros / cons

\+ invalid configuration fails at test setup, next to the cause, not in the 200th test;
\+ safe to share between tests, including parallel ones, with no synchronization;
\+ clients get simpler: `good.Retrier` has no guards and no sentinel values;
\+ the invariants are directly testable, including with properties ("for all valid policies...").

\- every "change" is a new object, and you write `with...` methods by hand;
\- a record publishes all its components; when the representation itself must be hidden, use a
  class instead (see `encapsulation.good.TestConfig`).

## When NOT to apply

- **Request DTOs for negative tests.** A `CreateUserRequest` that refuses an empty email cannot test
  that the SUT answers 400. Keep the payloads you send to the SUT permissive, and keep the framework's
  own objects (policies, configs, runs) strict.
- **Business rules of the SUT.** Do not copy them into the framework. If the framework rejects data
  before the SUT sees it, bugs in the SUT's validation stay invisible.
- **Plain data with no rules**, such as parameter rows or report lines. A record without checks is enough.

## Try it yourself

1. Add `Duration overallTimeout` with the invariant `overallTimeout >= sum of all delays`. Where
   does the check go, and does `withMaxDelay(..)` need to change?
2. Replace `int` status codes with a small `HttpStatus` value type, so a code outside 100..599 cannot
   exist at all. Which checks in `RetryPolicy` disappear?
3. Remove `Set.copyOf` and write the test that shows a "shallowly immutable" record being changed
   through the caller's set.
4. Add jitter (random part of the delay) while keeping `RetryPolicyPropertyTest` green. Hint: inject
   the randomness the same way `Sleeper` is injected.

Back to [invariants](../README.md).
