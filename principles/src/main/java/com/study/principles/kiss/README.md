# KISS and YAGNI — boolean-flag steps and flexibility for later

**KISS**: the simplest design that honestly solves *today's* problem. **YAGNI**: don't build for needs you
only predict. Both fight the same thing — complexity nobody exercises. A boolean flag adds a branch to one
method; an interface "for later" adds a mechanism. Each looks like flexibility; each is a path no test runs.
Simple is not the same as easy: one `placeOrder(..)` with six parameters is *easy* to extend and hard to
trust; five small intention methods are simple.

| Package | What to look at |
|---|---|
| `flags/bad` | `OrderSteps.placeOrder(customer, guest, promo, pay, savedCard, verify)` — one flag per test that needed "almost the same order"; `verify` checks only `PAID`, and only if `pay` |
| `flags/good` | `Checkout.asGuest()` / `as(customer)` return different carts (`GuestCart` has no `payWithSavedCard`); `withPromo`; `payWith` / `payWithSavedCard` → `PaidOrder`, `leaveUnpaid` → `PendingOrder`; no `verify` — the test asserts; `CreatedOrders` — cleanup by the lifecycle, not a flag |
| `flags` root | `FakeShop` with regression builds (`PROMO_BROKEN`, `PAYMENT_BROKEN`), `FlagAudit` — runs the flag step with all 32 combinations against all builds and gives each a verdict |
| `yagni/bad` | `Reporter` interface (one impl), `ReporterFactory` + `ReporterConfig` (`file`, `slack` "planned"), `CompositeReporter` nobody configures; `TreeReporter` — the reporter that really came, squeezed into `report(String)` |
| `yagni/good` | `v1.CheckoutSteps` — one class, prints, no abstraction; v2: `StepListener` (`started` / `finished`) extracted from two real consumers, `ConsoleSteps`, `StepTree`, `Steps.step(name, body)` |
| root | `KissDemo` |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.kiss.KissDemo
mvn -q -pl principles test -Dtest='FlagStepTest,YagniTest'
```

- `FlagStepTest` — **bad**: `FlagAudit` sorts the 32 combinations: **13 OK, 4 BROKEN** (a guest with a saved
  card — the signature accepts it, the shop throws), **7 LIES** (`verify` + `promo` passes on a shop whose
  promo is broken), **8 DEAD_FLAG** (`savedCard` or `verify` without `pay` change nothing — the caller thinks
  they asked for something). **good**: one test per intention, each regression is caught by the test that
  cares about it; the impossible combination does not compile; cleanup removes every created order.
- `YagniTest` — **bad**: four types and a config print four lines; the planned reporters are placeholders; the
  step tree, the need that really came, gets four roots instead of one tree — `Reporter` must change, and
  everything built around it. **good**: v1 is one class; v2 prints the same through the extracted listener,
  and the tree is `buy > [add to cart, pay > [confirm 3DS]]`; a failing step is still closed.

## Flags: why they rot

| | Flag step | Intention methods / types |
|---|---|---|
| Cases | 2^n, written down nowhere | one method per real case |
| Call site | `placeOrder("alice", true, false, true, false, true)` | `checkout.asGuest().withPromo("SAVE5").payWith(card)` |
| Impossible combination | accepted, fails at run time (or silently) | does not compile |
| `verify` inside the step | checks what the author of the first version cared about | the test asserts what *it* cares about |
| Adding a case | one more `if`, one more parameter at every call site | one more method; existing callers untouched |
| Review | "it's just a flag" | the type tells you what is possible |

Fixes, cheapest first: split into intention methods (`payAsGuest`, `leaveUnpaid`); replace two related
booleans with an enum (`Payment.NEW_CARD | SAVED_CARD | NONE`); let the first choice return a type that
offers only the valid next steps (`GuestCart` / `CustomerCart`); move cross-cutting flags (`verify`,
`cleanup`, `log`) out of the step altogether.

## YAGNI: what speculation costs

- **The guess is usually wrong in shape**, not only premature. `report(String)` fit the console; the real
  second consumer needed start and end. The interface changes anyway — together with the factory, the
  composite and the config built on top of it.
- **Speculative code is untested code**: `CompositeReporter`, the `file` branch, `flush()` — no suite uses
  them, so nobody knows whether they work.
- **It costs every reader**: four types to follow before you find the `println`.
- **Extract from real uses**: with two consumers you can *see* what the interface is. The rule of three: the
  third similar case is when the pattern is reliable enough to generalise (see [`ocp`](../ocp/README.md) for switches).

What is *not* YAGNI: seams that are cheap and needed **now**, such as constructor injection so a test can pass
a fake ([`coupling`](../coupling/README.md)), an injected `Clock` ([`time`](../time/README.md)), or the
nested `step(name, body)` in v1 — the steps *are* nested today; the guess would be about reporters.

## Pros / cons

| Pros | Cons |
|---|---|
| Every path exists because a test needs it, so every path is exercised | More methods / small types than one flagged step |
| Illegal combinations are compile errors, not flaky runs | Rework when the need arrives (cheap if the code is simple) |
| Readers find the behaviour without crossing factories and configs | Judgement: "needed now" vs "needed soon" is not always clear |
| The interface, when it comes, is shaped by real consumers | Teams that change slowly (shared libraries, public APIs) pay more for later changes |

## When NOT to apply
- **A flag that is a value, not a mode** — `headless`, `timeout`, `locale`: a parameter of one behaviour,
  no branches on combinations. Fine as a field in config.
- **Library / public API boundaries** — when other teams depend on you and a breaking change costs a release
  cycle, design the extension point up front (a step listener in a shared framework). YAGNI is cheapest
  inside one repository.
- **Known, scheduled requirements** — "the mobile run starts next sprint" is a plan, not speculation.
- **KISS is not "no abstraction"**: copy-pasting request code into every test is the wet column of
  [`dry`](../dry/README.md); `Thread.sleep(5000)` is "simple" and wrong
  ([`concurrency/eventual`](../concurrency/eventual/README.md)).

## Try it yourself
1. Add a sixth flag `cleanup` to `OrderSteps` (delete the order at the end of the step). Extend `FlagAudit`.
   What does `cleanup` + `verify` + returning the order give?
2. The product adds gift cards (guest and customer). Add them to `bad` and to `good`. Count the touched call
   sites and the new audit rows.
3. Replace `guest` + `savedCard` with an enum in `bad`. Which verdicts disappear, which stay?
4. A third real consumer arrives: a "slow step" detector that needs the duration. Does `StepListener` need to
   change? (Measure in `Steps`, pass it to `finished`?) This is the rule of three at work.
5. Smell check on your framework: methods with two or more `boolean` parameters, call sites with
   `true, false`, interfaces with exactly one implementation outside test fakes, factories whose `switch`
   has one used branch, `default` methods "for future implementations", config keys every suite sets the same.
