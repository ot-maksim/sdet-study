# Abstract class: shared state with an invariant

Part of [inheritance vs composition](../README.md): this is the case where inheritance is the *right* tool;
[`basetest`](../basetest/README.md) and [`pages`](../pages/README.md) show where it is not.

**Invariant** — a condition that must hold for the object's whole life.
An abstract class is the right tool when several implementations share the same state *and* the same rules for it:
the base class keeps the state `private`, exposes `final` public methods that enforce the rules,
and gives subclasses only `protected abstract` hooks (Template Method).

Session lifecycle invariants:
1. Transitions go only `NEW → STARTED → CLOSED`.
2. Actions are allowed only in `STARTED`.
3. `close()` is idempotent.
4. If `start()` fails halfway, already acquired resources are released.

`BrowserFarm` / `FakeBrowser` are fakes for Playwright/Appium: they count open browsers, so a leak is visible without real browsers.

| Package | What to look at |
|---|---|
| `bad` | `protected` flags + all methods abstract → `WebSession` and `MobileSession` each break the rules differently |
| `good` | `DriverSession` owns state; subclasses implement only `doStart()` / `doRelease()` |
| `composition` | Same invariants in a `final ManagedSession` + injected `SessionDriver` (HAS-A instead of IS-A) |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.inheritance.session.SessionDemo
mvn -q -pl principles test -Dtest='BadSessionTest,SessionInvariantsTest'
```

- `BadSessionTest` — characterization tests: they pass *because* they pin the bugs.
- `SessionInvariantsTest` — one contract test for every correct implementation (also an LSP check).

## Abstract class vs composition here

| | `good` (abstract class) | `composition` |
|---|---|---|
| Coupling | Subclass is tied to the base class | Driver knows nothing about the lifecycle |
| Adding a driver | Extend + 2 hooks | Implement 2 methods |
| Decorating (logging, retry) | Awkward: another subclass level | `new ManagedSession(new LoggingDriver(new WebDriver(...)))` |
| Testing the lifecycle itself | Needs a test subclass | Inject a fake `SessionDriver` |
| Cost | Fewer objects, simpler for one shallow level | One more object/indirection |

Rule of thumb: one level of hierarchy and a stable contract → abstract class is fine.
Several axes of variation (driver × logging × retry × reporting) → composition.

## Try it yourself
1. Fix `bad.WebSession` so double close works. Which `BadSessionTest` goes red? Now fix the same in `bad.MobileSession` — notice the duplication.
2. In `good.DriverSession` remove `final` from `close()` and write a subclass that "optimizes" it. Which invariant test catches it? (Hint: add your class to `SessionInvariantsTest.sessions()`.)
3. Add `ApiSession` (holds an auth token: acquire = login, release = logout) in both `good` and `composition`. Count the lines.
4. Write a `LoggingDriver implements SessionDriver` decorator. Try to do the same with the `good` hierarchy — what goes wrong?
5. Make `ManagedSession` thread-safe for parallel tests. Where does the change go in each variant?
