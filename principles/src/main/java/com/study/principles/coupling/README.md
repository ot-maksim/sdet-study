# Coupling and dependency injection

**Coupling** is how much one module must know about, and change with, another. The tightest kind for a
dependency is *creating* it (`new Browser()` inside a page) or *fetching* it from a global
(`DriverManager.get()`): the module then also decides which implementation, when it starts, who shares it
and when it dies. **Dependency injection** moves that decision out — the module states what it needs in its
constructor and somebody else (the **composition root**) builds the graph and owns lifetimes.

Abstractions owned by the high-level side (DIP: the test depends on `UserPrecondition`, not on JDBC) are in
[`dip`](../dip/README.md). Scopes (run / class / test) and the `ThreadLocal` holder under parallel runs are
in [`parallel`](../parallel/README.md). This kata is about **who creates, who owns, how long it lives**.

| Package | What to look at |
|---|---|
| `bad/newinside` | `LoginPage` and `DashboardPage` each do `new FakeBrowser()`: login happens in one browser, the dashboard opens in another and is redirected to `/login`. `getBrowser()` exists only so tests can close them |
| `bad/locator` | `DriverManager` static holder: `new LoginPage()` claims to need nothing, fails with NPE on the first action, and a test inherits the closed browser the previous test left in the holder |
| `bad/captive` | `LoginSteps.instance(browser)`: constructor injection *and* a lazy singleton — it captures the first test's browser and uses it (closed) in every later test |
| `good` | `LoginPage(Browser)` / `DashboardPage(Browser)`: constructor injection; navigation returns the next page over the same browser. `Pages` — factory bound to one browser. `Browsers` — factory for extra sessions that also owns and closes them |
| `good/root` | `UiExtension` — composition root as a JUnit 5 `ParameterResolver`: the only `new FakeBrowser`, one `Browsers` per test in the test's `Store`, closed by JUnit even when the test fails |
| root | `Browser` (interface), `FakeBrowser` (in-memory app with a login session per browser), `CouplingDemo` |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.coupling.CouplingDemo
mvn -q -pl principles test -Dtest='BadCouplingTest*,GoodPagesTest,UiExtensionTest'
```

- `BadCouplingTest` — characterization, one nested class per smell. Note the `@AfterEach` in each: by hand
  closing, resetting a global, resetting a singleton — cleanup knowledge spread over tests.
- `GoodPagesTest` — pages are plain objects: `new LoginPage(anyBrowser)`; a decorator slots in without
  touching pages; `Browsers` closes everything it launched.
- `UiExtensionTest` — the composition root's contract through EngineTestKit: one session per test, a new
  browser per test, closed after it, also when the test fails.

## Terms that get mixed up

| Term | What it is | Here |
|---|---|---|
| **DIP** | a *design rule*: high-level code depends on abstractions it owns | `Browser` interface, pages don't know Playwright — see `dip` |
| **IoC** | a *control shift*: someone else calls you / builds your graph (frameworks, JUnit calling your test) | JUnit resolves test parameters via `UiExtension` |
| **DI** | a *technique*: dependencies are passed in (constructor, method parameter), not created or looked up | `new LoginPage(browser)` |
| **Service Locator** | dependencies are *pulled* from a registry by the user | `DriverManager.get()` — an anti-pattern for application code |
| **Composition root** | the single place where the graph is assembled, close to the entry point | `UiExtension` (later: Spring context, stage 4) |

DI does not need a container. A container is a composition root that builds itself by reflection —
worth it when the graph is large; for a test framework skeleton, a hand-written root is clearer (stage 2).

## Coupling, from tight to loose — automation examples

| Kind | Example | Cost |
|---|---|---|
| Content | the test reaches `page.getBrowser().text(..)` / reflection into a field | any internal change breaks tests |
| Global (common) | `DriverManager`, static `Config`, `static WebDriver` | hidden in signatures; order-dependent tests; parallel runs break |
| Creation | `new FakeBrowser()` / `Playwright.create()` inside a page | fixed implementation, no sharing, no owner |
| Control | `click(selector, boolean waitForNavigation, boolean retry)` | the caller steers the callee's internals |
| Stamp | `fillForm(TestContext ctx)` when it reads two fields of it | depends on a whole structure for a part |
| Data | `loginAs(user, password)` | the loosest useful kind — aim here |

## How to inject

| Way | When | Watch out |
|---|---|---|
| Constructor | default: required dependencies, fixed for the object's life | 6+ parameters = the class does too much (SRP), not a reason for a container |
| Method parameter | the dependency varies per call, or the framework supplies it (JUnit test parameters) | not for things every method needs |
| Factory (`Pages`, `Browsers`, `Supplier<T>`) | the object needs *many* instances or decides *when* to create them | inject a narrow factory, never the container ("give me anything" = locator) |
| Setter | optional dependency with a sane default | a required one via setter is temporal coupling — see [`deep`](../deep/README.md) |

## Lifetimes: the captive dependency rule
An object may only hold dependencies that live **at least as long** as it does.

| Holder → held | OK? |
|---|---|
| per test → run-wide (page → config) | yes |
| per test → per test (page → that test's browser) | yes, same owner |
| run-wide → per test (singleton steps → browser, `static` page cache, `@BeforeAll` page) | **no** — works for test 1, dead afterwards |

## Pros / cons

| Pros | Cons |
|---|---|
| Signatures tell the truth: what you need is what you get | Someone must write the composition root and keep it in one place |
| Pages are plain objects: unit-testable with a fake, decoratable (tracing, screenshots) | Navigating "where does this come from" goes through the root, not the class |
| One owner per resource → closing is never forgotten, also on failure | Passing dependencies through layers that don't use them ("tramp data") if the graph is poorly shaped |
| Swapping implementations (grid, WebKit, fake) is a change in the root only | Over-abstraction: an interface for every class with one implementation |

## When NOT to apply
- **Values and data** — `new User("alice")`, `new NewUser(..)`, `Duration.ofSeconds(5)`: `new` is fine; only
  *services* with behaviour, lifetime or alternatives are injected.
- **Stable, side-effect-free helpers** — `String.format`, a JSON mapper with fixed settings, `Math`; injecting
  them adds parameters, not freedom.
- **Navigation inside a page family** — `loginAs(..)` returning `new DashboardPage(browser)` is not a smell:
  it passes the injected browser on; creating the browser would be.
- **A DI container for five classes** — a hand-written root is shorter and debuggable. Containers pay off
  with many modules, scopes and profiles (stage 4).
- **Injecting what a test should state** — test data and expected values belong in the test body, not in
  a resolver that hides them (DAMP over DRY, block 1c).

## Try it yourself
1. Add a `ScreenshotOnFailure` behaviour. In `bad/newinside`, which browser do you screenshot? In `good`,
   where does the code go (hint: the root knows the browser *and* the test outcome)?
2. Make `bad/locator.DriverManager` a `ThreadLocal` and run two tests in parallel. Which bugs went away,
   which stayed, which appeared? Compare with `parallel.threadlocal`.
3. Fix `bad/captive.LoginSteps` in two ways: per-test instance, or a stateless singleton that takes the
   browser as a method parameter. Which one would you keep and why?
4. Add a "remote grid" mode: `-Dbrowser=remote`. Count the files that change in `bad/newinside` vs `good`.
5. Smell check on your framework: grep for `getDriver()` outside the root, `static .*Driver`, `new .*Page(`
   without arguments, `getInstance(`, and classes with more than five constructor parameters.
