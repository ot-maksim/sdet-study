# Parallel tests: share only what cannot change

Between parallel tests you may share only **immutable** things: constants, records, `List.of(..)`,
stateless functions and filters, clients whose "setters" return a new object. Everything a test can
change is created **per test** and reached by **explicit passing**, never through a static. The few
things that must be shared and are mutable get a mechanism **built for that kind of sharing**:
compute once, count, append, lease, lock.

A static mutable field is not a parallel bug; it is an **order** bug that parallelism turns from
"depends on test order" into "depends on timing".

| Package | What to look at |
|---|---|
| root | `FakeShop` (the SUT: thread-safe; premium accounts allow one session; global and per-user feature flags), `FakeShopHttp` (a RestAssured `Filter` that answers in-process: real RestAssured, no network), `FakeWebDriver` (a browser session: not thread-safe, like the real one), `ParallelDemo` |
| `bad` | `BaseShopTest`: `static RequestSpecification`, `static WebDriver`, `counter++`, `static HashMap`, one premium account for everybody |
| `good` | `ShopApi` (immutable client), `ProfilePage` (page object over the test's driver), `ShopEnvironment` (run-wide state, each field shareable for a stated reason), `AccountPool` (`BlockingQueue` leases), `TestUsers` (per test), `ShopExtension` (composition root: the scope of every object; refuses per-test objects at class level) |
| `threadlocal` | `DriverManager`: the classic `ThreadLocal<WebDriver>` fix and where it breaks |
| `testng/bad` | `ShopNgTestBase`: per-test objects in instance fields set in `@BeforeMethod` (TestNG has one instance per class) |
| `testng/good` | `ShopNgTestBase`: the same scopes as `ShopExtension` built from TestNG parts: holder idiom for the run, an `ITestResult` attribute per test, `@SharedResource` + `IHookable` as the lock |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.parallel.ParallelDemo
mvn -q -pl principles test -Dtest='com.study.principles.parallel.**.*Test'
```

- `ParallelDemo` — the same "new user sees own name via API and in the UI" test, 400 times on 8 threads.
  Bad: ~20% fail, with a different mix every run (someone else's token, someone else's page, duplicate
  "unique" names, 403 from a non-admin token). Good: 400 ok, one admin login.
- `BadSharedStateTest` — characterization: `TwoThreads` scripts the interleaving, so each race is a
  deterministic, readable scenario. One test needs no threads at all: the header leak.
- `GoodSharedStateTest` — the same scenarios isolated; an explicitly passed driver on another thread; then
  the shared state that cannot be avoided: a scarce account, a global feature flag (and the per-user
  override that removes it), counters and a cleanup registry under 8 real threads.
- `ThreadLocalDriverTest` — fixes the race between threads; breaks on another thread inside one test and
  on a reused pool thread.
- `ShopParallelTest` — a real `@Execution(CONCURRENT)` JUnit run with `ShopExtension`. Never flakes.
- `FeatureFlagLockTest` — `@ResourceLock` `READ_WRITE` / `READ` on a global flag under a real parallel run;
  counters inside the tests check the lock. Without the two annotations: 20 of 30 fail.
- `ShopExtensionScopeTest` — runs small test classes through the JUnit engine (EngineTestKit): every
  parallel method gets its own driver, a page object and `@BeforeEach` get the same one as their test,
  and asking for a driver in `@BeforeAll` or a `PER_CLASS` constructor fails with a clear message.
- `testng/JUnitVsTestNgLifecycleTest` — executable facts, both engines: instances per method vs per class,
  a field from the per-test setup in parallel, `@Factory` + `parallel="instances"`, the `timeOut` thread.
- `testng/TestNgSharedStateTest` — the profile scenario on the bad and the good TestNG base classes.
- `testng/ShopNgParallelTest` — the TestNG twin of `ShopParallelTest` + `FeatureFlagLockTest`, run by the
  normal build with `parallel=methods`. Without `@SharedResource`: 19 of 70 fail.

`src/test/resources/junit-platform.properties` enables parallel execution with `same_thread` as the
default, so only classes annotated `@Execution(CONCURRENT)` run in parallel. TestNG classes run on the same
JUnit Platform through the TestNG engine (`org.junit.support:testng-engine`), with `testng.parallel=methods`
from the same file: one `mvn verify` runs Jupiter, jqwik and TestNG.

## 0. What runs in parallel: tests or classes

Design every object for the **smallest** unit your runner may execute in parallel. In practice that is
the test (method or scenario), never the class: one switch in the config moves you from "classes in
parallel" to "methods in parallel", and class-level state silently becomes shared.

| Runner | Parallel unit (configurable) | Shared between parallel tests by default | Typical trap |
|---|---|---|---|
| JUnit 5 | `mode.default` = methods within a class, `mode.classes.default` = classes; any combination; both `same_thread` by default | `static` fields. A new instance per test method | `@TestInstance(PER_CLASS)`: one instance, its fields shared by concurrent methods. Per-test objects created in `@BeforeAll` |
| TestNG | `parallel="methods" / "classes" / "tests" / "instances"`, `thread-count`; `@DataProvider(parallel = true)` | `static` **and instance fields**: one instance of a class serves all its methods | fields of the test class with `parallel="methods"`, which is why `ThreadLocal<WebDriver>` is so common there |
| Cucumber (JUnit Platform engine) | the **scenario**: `cucumber.execution.parallel.enabled=true`; `cucumber.execution.execution-mode.feature=same_thread` keeps one feature's scenarios in order | `static`. Step definition classes are new per scenario | a `static ScenarioContext` / `Map<String, Object>` to pass data between step classes |

The same scopes in each runner:

| Scope | JUnit 5 | TestNG | Cucumber |
|---|---|---|---|
| Run | root `ExtensionContext.Store` | `@BeforeSuite` into an `ISuite` attribute or a lazy holder | static `@BeforeAll` hook, lazy holder |
| Test | the test's `Store` via `ParameterResolver` (`@BeforeEach` shares it) | an attribute of the test's `ITestResult`: set in `@BeforeMethod(ITestResult)`, closed in `@AfterMethod(alwaysRun = true)`, read via `Reporter.getCurrentTestResult()` (§8) | constructor injection (PicoContainer / Spring `@ScenarioScope` / Guice `ScenarioScoped`): one object per scenario, shared by all its step classes; `@After` hook closes it |

## 1. The design toolbox

Nine mechanisms. Each answers one question about one object; together they cover everything a test
framework holds.

| # | Mechanism | Use when | Tools | In this kata |
|---|---|---|---|---|
| 1 | **Immutability** | the object can be a value: config, credentials, test data templates, a client | `record` + `List.copyOf`/`Map.copyOf` in the compact constructor; final fields; "withers" that return a new object | `ShopConfig`, `Credentials`, `ShopApi.as(..)` / `withLanguage(..)` |
| 2 | **Confinement: one per test** | the object is mutable by nature: browser session, page objects, a request being built, the test's own lists | create it in the test's scope; the creator closes it; no synchronization needed inside | `FakeWebDriver`, `ProfilePage`, `TestUsers` (its `ArrayList` of leases is fine: one test owns it) |
| 3 | **Scoping by a composition root** | someone must decide lifetimes (run / class / test) and close things | JUnit `ParameterResolver` + `ExtensionContext.Store` (root, class, method); refuse a per-test object in a class-level context | `ShopExtension` |
| 4 | **Explicit passing** | code deep inside (pages, steps, async lambdas) needs the per-test object | constructor and method parameters; lambdas capture the local variable. Not a static holder, not a `ThreadLocal` lookup | `new ProfilePage(driver)`; `explicitlyPassedDriver_worksOnAnyThreadOfTheTest` |
| 5 | **Isolation by data** | the SUT is shared (it always is): users, orders, accounts, flags | each test creates its own data; unique names with a run id; switch features **per user**, not globally | `TestUsers.create()`, `ShopApi.setFeatureFor(..)` |
| 6 | **Compute once** | an expensive or rate-limited value every test needs: admin token, parsed schema, reference data | JUnit root `Store.getOrComputeIfAbsent` (thread-safe, at most once, closed at the end); outside JUnit: holder idiom, `ConcurrentHashMap.computeIfAbsent` | `ShopEnvironment` (admin login once per run) |
| 7 | **Atomic and concurrent structures** | many tests update one thing: a counter, a registry to clean up, a cache | `AtomicLong`/`LongAdder`; `ConcurrentLinkedQueue`, `ConcurrentHashMap.newKeySet()`; `ConcurrentHashMap` with atomic methods (`merge`, `computeIfAbsent`), not `get`-then-`put` | `ShopEnvironment.names`, `createdUsers` |
| 8 | **Exclusive lease** | a scarce resource that cannot be created per test: pre-provisioned premium/payment accounts, devices, phone numbers | `BlockingQueue` pool: `poll(timeout)` / `offer`; release in the per-test `close()`; fair queue; timeout with a clear message | `AccountPool`, `TestUsers.leasePremium()` |
| 9 | **Declarative lock** | a global switch you cannot make per test: a feature flag without targeting, SUT clock, maintenance mode, JVM-wide settings | `@ResourceLock(value, READ_WRITE)` for tests that change it, `READ` for tests that depend on it; `Resources.SYSTEM_PROPERTIES`, `LOCALE`, `TIME_ZONE`; `@Isolated` as the last resort | `FeatureFlagLockTest` |

The tools column is JUnit 5; the TestNG parts for mechanisms 2, 3, 6 and 9 are in §8.

**How to choose**, per object, in this order (stop at the first "yes"):
1. Can it be immutable? → share it freely (1).
2. Can each test own one? → create it per test (2), let the composition root own the lifetime (3), pass it explicitly (4).
3. Can the test's own data replace the shared data? → isolate by data (5).
4. It really is shared. What kind of sharing? Read after one expensive computation (6); count or append (7);
   use exclusively (8); switch globally (9).

Each step down costs more: 1–5 cost nothing at run time, 6–7 almost nothing, 8 and 9 **serialize** the
tests that use them. A lock is a confession that the state could not be isolated; prefer pushing it up the
list (a per-user flag beats `@ResourceLock`: compare the third and the first test in `FeatureFlagLockTest`).

**Not in the toolbox:**

| Looks like a fix | Why it is not |
|---|---|
| `static` field "because it is expensive" | expensive and immutable → compute once (6); expensive and mutable → share the factory (Grid, `Browser`), create the product per test |
| `synchronized` around a shared driver or spec | thread-safe code, still wrong tests: A and B take turns on one session, and the suite runs serially |
| `ThreadLocal` in JUnit 5 / Cucumber | the right scope is the test, not the thread (§5) |
| retry / `sleep` until the race stops showing | hides the shared state; the next timing change brings it back |
| `@Isolated` on everything that ever flaked | correct and slow; find the shared state and move it up the list |

## 2. RestAssured `RequestSpecification`

`RequestSpecification` is a **mutable builder**: its methods change the object and return `this`.
Checked against RestAssured 5.5.0 (`BadSharedStateTest` runs real RestAssured):

| Call on a shared `static` spec | Effect |
|---|---|
| `spec.auth().preemptive().oauth2(token)` | **overwrites** the auth for every test: last writer wins. Sequential run: fine (each test sets it before use). Parallel: test A sends test B's token |
| `spec.header("Accept-Language", "de")` | **appends**: `[de]`, then `[de, en]`... forever. Fails even sequentially, depending on test order (servers usually take the first value) |
| a test that never authorizes | still sends the token of whatever test ran before it: "anonymous gets 401" passes alone, fails in the suite |
| `spec.given()` | returns the same object (syntactic sugar), not a copy |
| `RestAssured.requestSpecification = spec`, `RestAssured.baseURI`, `RestAssured.filters(..)` | the same problem one level up: global static configuration |
| `given().spec(spec).header(..)` | safe: `given()` creates a new spec and **copies** `spec` into it; `spec` is not changed |

**Good** (`ShopApi`), mechanisms 1 and 2: share the recipe, not the product.
- The shared thing is an immutable `ShopConfig` record and a stateless `Filter`.
- The mutable spec is created by `given()` for **one request** and never leaves `request()`.
- Per-test differences are new objects: `api.as(token).withLanguage("de")` returns a new client; `api` is
  untouched. Because the client is immutable, "per test or shared?" stops mattering: `ShopEnvironment`
  keeps one admin client for every test.

Acceptable middle ground: `private static final RequestSpecification BASE = new RequestSpecBuilder()...build()`
used only as `given().spec(BASE)`. It is immutable **by convention**: nothing stops the next person from
writing `BASE.header(..)`, so never expose it.

## 3. WebDriver, page objects, steps

A browser session is mutable by nature (cookies, current page, open windows) and not thread-safe; there is
no immutable version of it. So: **one per test**, owned by whoever created it, closed by the same owner.

| Shared `static WebDriver` | Effect |
|---|---|
| test B logs in | test A's next `findElement` reads B's page with B's cookies |
| test B's `@AfterEach` calls `quit()` | test A's browser is gone mid-test |
| Selenium from two threads at once | interleaved commands, `NoSuchSessionException`, random timeouts |

**Good**: `ShopExtension` creates a driver in the **test's** `ExtensionContext.Store`; JUnit closes stored
`AutoCloseable`s when the test ends, even if it failed. Everything built on the driver is per test too:
`ProfilePage` takes the driver in its constructor, and the extension builds it over the same driver the
test and its `@BeforeEach` get. Steps/facades over pages and clients follow the same rule: the whole object
graph of a test is created per test, wired by constructors. The usual leaks are page objects as static
singletons (`Pages.profile()`) and page objects that fetch the driver from a static holder.

Expensive things are shared one level up, never the session itself:

| Tool | Share (run or thread) | Create per test |
|---|---|---|
| Selenium | Grid / driver binary / `DriverService` | `WebDriver` session |
| Playwright Java | `Playwright` + `Browser` **per thread** (Playwright objects must stay on the thread that created them) | `BrowserContext` + `Page` |

## 4. Library objects: share or not

| Object | Share between parallel tests? |
|---|---|
| RestAssured `RequestSpecification` / `ResponseSpecification` | no: mutable builders. `given()` per request |
| RestAssured statics (`baseURI`, `requestSpecification`, `filters`, `config`) | set once before any test, never from a test |
| `ObjectMapper` (Jackson), `Gson` | yes, once configured. Never call `configure(..)` later; derive `ObjectReader`/`ObjectWriter` (immutable) for variants |
| `java.net.http.HttpClient`, Apache `CloseableHttpClient` with a pooling manager | yes: built to be shared |
| JDBC `DataSource` (HikariCP) | yes. A `Connection` is not: per test, or borrowed from the pool per call |
| `DateTimeFormatter`, `Pattern`, `BigDecimal`, records | yes: immutable |
| `SimpleDateFormat`, `MessageDigest`, `StringBuilder`, `HashMap`, `ArrayList` | no: create per use, or confine to one test |
| `Random` | thread-safe but contended and not reproducible across threads: a seeded `Random` per test, or `ThreadLocalRandom.current()` per call |
| `WebDriver`, Playwright `BrowserContext` / `Page` | no: per test (§3) |
| `System.setProperty`, `Locale.setDefault`, `TimeZone.setDefault` | JVM-global: `@ResourceLock(Resources.SYSTEM_PROPERTIES / LOCALE / TIME_ZONE)` on every test that changes or reads them |

## 5. `ThreadLocal<WebDriver>`

The usual fix for a static driver (Selenide and many in-house `DriverManager`s). It does remove the race
between threads, but it scopes the browser to a **thread**, while the right scope is a **test**.
In **TestNG** it is the usual answer to shared instance fields, and it mostly works because `@BeforeMethod`,
the test and `@AfterMethod` share a thread; but a `@Test(timeOut = ..)` run without parallelism executes the
body on its own thread, and the test's own `ITestResult` is the better per-test home (§8). JUnit 5 and
Cucumber have a real per-test scope, and there ThreadLocal only adds the traps below:

| Where they differ | Effect |
|---|---|
| The test uses another thread: Awaitility (evaluates conditions in its own thread by default), `CompletableFuture`, `@Timeout(threadMode = SEPARATE_THREAD)` | no driver there, or another test's. An explicitly passed driver works on any thread (mechanism 4) |
| Pool threads are reused | a missed `remove()`/`quit()` hands a logged-in browser to the next test on that thread |
| `static` access | any class can call `DriverManager.driver()`; nothing says which test owns it |

Use `ThreadLocal` when the resource itself is thread-bound (Playwright), and still create the isolation
unit (`BrowserContext`) per test.

## 6. Checking that a design is parallel-safe

Running the suite in parallel and seeing it green proves little: races show up at some timings only.

| Technique | What it proves | In this kata |
|---|---|---|
| Scripted interleaving on two real threads | a given race is impossible (or, for bad code, certain): deterministic, readable | `TwoThreads` in `Bad/GoodSharedStateTest`, `ThreadLocalDriverTest` |
| Stress with an invariant | no lost updates or duplicates under load: N threads, then assert exact counts | `runWideState_underRealConcurrency` |
| Self-checking parallel run | the runner's guarantees hold (locks, scopes): counters inside the tests | `FeatureFlagLockTest` |
| Running test classes through the engine | the framework's scope rules: what is shared, what is refused | `ShopExtensionScopeTest` (EngineTestKit) |
| Removing the guard and watching it fail | the guard is needed, the test can fail | `FeatureFlagLockTest` without `@ResourceLock`: 20 of 30 fail |

## 7. JUnit configuration

```properties
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.default=concurrent          # methods within a class
junit.jupiter.execution.parallel.mode.classes.default=concurrent  # classes
junit.jupiter.execution.parallel.config.strategy=fixed
junit.jupiter.execution.parallel.config.fixed.parallelism=8
junit.jupiter.execution.parallel.config.fixed.max-pool-size=8
```

- **Size by what the tests wait for**, not by CPU cores: UI and API tests mostly wait for the browser
  grid and the stand. Parallelism = sessions the grid gives you and load the stand takes.
- `dynamic` (default) = cores × factor: right for CPU-bound unit tests, usually wrong for E2E.
- The ForkJoin pool can run **more** threads than `parallelism` while some tests block (this kata's run
  showed 5 workers for 4). Cap it with `max-pool-size` when sessions are a hard limit.
- Opt classes in or out: `@Execution(CONCURRENT)` / `@Execution(SAME_THREAD)`; `@Isolated` runs a class
  alone; `@ResourceLock` serializes only the conflicting tests (§1, mechanism 9).

## 8. JUnit 5 vs TestNG

Same nine mechanisms (§1), different parts to build them from. Every row is executed in
`testng/JUnitVsTestNgLifecycleTest` or `testng/TestNgSharedStateTest`.

| Question | JUnit 5 | TestNG | Consequence for TestNG code |
|---|---|---|---|
| Who owns an instance field? | **one test**: a new instance per test method and per `@ParameterizedTest` invocation | **the class**: one instance runs all methods and all `@DataProvider` invocations | per-test objects must not be fields with `parallel="methods"`, `@DataProvider(parallel = true)` or `threadPoolSize` (`testng/bad`) |
| What is the parallel unit? | per class or package via `@Execution`; methods and classes switched separately | suite-wide `parallel="methods" / "classes" / "tests" / "instances"` in `testng.xml`; `data-provider-thread-count` | the setting for one suite applies to every class in it: a base class must be safe for `methods` |
| How does a test get its objects? | `ParameterResolver`: typed parameters of tests and lifecycle methods | no custom injection (only `ITestResult`, `ITestContext`, `Method`, data provider values; Guice via `@Guice`) | accessors in a base class (`driver()`, `profile()`) over a per-test holder |
| Where do per-test objects live? | the test's `ExtensionContext.Store`, closed by JUnit | the test's `ITestResult` attributes: `@BeforeMethod(ITestResult)` puts, `@AfterMethod(alwaysRun = true)` closes, `Reporter.getCurrentTestResult()` reads | `testng/good/ShopNgTestBase` |
| Which thread runs the body? | any ForkJoin worker; the same as `@BeforeEach` unless `@Timeout(SEPARATE_THREAD)` | the same as `@BeforeMethod`, **except** `@Test(timeOut)` without parallelism: its own thread | a hand-made `ThreadLocal` is empty there: green in CI (parallel), red when one test runs from the IDE |
| Run-wide state | root `Store`: lazy, thread-safe, closed at the end | `@BeforeSuite`/`@AfterSuite` (once per suite even from a base class) or the holder idiom | `ShopNgTestBase.Run`: holder idiom + `@AfterSuite(alwaysRun = true)` |
| Instance as the unit of isolation | not needed: the instance is already per test | `@Factory` + `parallel="instances"`: an instance per data row, its methods in sequence on one thread | fields **are** per test then; good for data-driven suites over few methods |
| Locking a shared resource | `@ResourceLock(READ / READ_WRITE)`: the scheduler does not start conflicting tests | none built in: `singleThreaded = true` (whole class on one thread), groups in a separate non-parallel `<test>`, or a hand-made lock (`@SharedResource` + `IHookable`) | a waiting test holds a pool thread, and its `@BeforeMethod` already opened a browser: lock only short sections |
| Refusing the wrong scope | the extension throws for a per-test object in `@BeforeAll` / `PER_CLASS` | the accessor throws when there is no current test (`@BeforeClass`, foreign threads) | the same guard, from a different place |

**When TestNG makes it easier:** data-driven suites (`@Factory`, `parallel="instances"`), suite-level
grouping and ordering in `testng.xml`. **When JUnit makes it easier:** per-test state by default, injection,
declarative locks, scoped cleanup. Neither is "parallel-safe" by itself: the design (§1) is.

## Pros / cons

| Pros | Cons |
|---|---|
| Parallel runs are deterministic: no "passes on rerun" | More objects: a client or browser per test (cheap compared with a flaky suite) |
| Order independence for free: any test can run alone or in any order | Run-wide state needs an owner (extension, Store) and explicit cleanup |
| Immutable clients need no locks and no reasoning about who changed what | Immutable "withers" (`as`, `withLanguage`) are more code than setters |
| The scope of every object is decided in one place (`ShopExtension`) | Scarce resources and global switches cap the parallelism of the tests that need them |
| Failures point at the test, not at whoever touched the static field last | Per-test browsers cost real time with real drivers: share the Grid/`Browser`, not the session |

## When NOT to apply
- **Don't make immutable what is confined anyway.** A list built and read inside one test method needs
  no `List.copyOf` and no concurrent collection.
- **Don't reach for concurrent collections first.** If each test can own the thing, ownership is simpler
  than a `ConcurrentHashMap`. Concurrent structures are for state that is shared by its nature.
- **Don't `synchronized` your way out** of a shared driver or spec (§1, "Not in the toolbox").
- **Don't create per test what is immutable and expensive**: a parsed schema, a config, an `ObjectMapper`
  (thread-safe once configured) are run-wide.
- **Don't lock what you can isolate.** `@ResourceLock` on a feature flag that supports per-user targeting
  serializes tests for nothing.
- **Don't use one stand for parallel runs of different branches** without a run id in test data.

## Try it yourself
1. Write `BadParallelTest extends BaseShopTest` with `@Execution(CONCURRENT)` and
   `@RepeatedTest(50)` copying `ParallelDemo.ProfileTest.run()`. Run it three times; compare the failures.
   Then run it with `junit.jupiter.execution.parallel.enabled=false`: why does it pass now?
2. "Fix" `BaseShopTest` with `synchronized` on every helper. Do the tests in exercise 1 pass? Why not?
3. Remove `@ResourceLock` from `oldProfileByDefault` only. Which tests fail now, and why do the per-user
   tests never fail?
4. `time/clock/TestUsersTest` changes the default time zone. What breaks if its class becomes
   `@Execution(CONCURRENT)` together with `time/format` tests, and which built-in `Resources` lock fixes it?
5. The admin token expires after 15 minutes. Replace the `final String adminToken` with a cache that
   refreshes it (see `time/clock/good/AuthTokenCache`). Which thread-safety mechanism does it need, and
   why does the per-test code not change at all?
6. Make `FakeShopHttp` log every request into an `ArrayList` "for the report". Run `ShopParallelTest`.
   What is now shared mutable state, and which mechanism from §1 fits?
7. Add `CheckoutSteps(ShopApi api, ProfilePage profile, TestUsers users)` and let `ShopExtension` resolve it
   per test. What must be true of its fields so it never needs a lock?
8. Move `ShopNgParallelTest` to `parallel="classes"` and add a second test class. Which of its guarantees
   still need `@SharedResource`, and which now come from the runner?
9. Port `ShopParallelTest` to Cucumber in your head: which object becomes the scenario-scoped
   `ShopContext` injected into `ApiSteps` and `UiSteps`, and where does `ShopEnvironment` live?
10. Smell check on your framework: `grep -rn 'static .*\(RequestSpecification\|WebDriver\|Page\b\|Map<\|List<\)\|static int\|RestAssured\.\(baseURI\|requestSpecification\|filters\)\|ThreadLocal'`.
   For each hit: which mechanism from §1 replaces it?
