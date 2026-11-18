# `BaseTest` god class vs composable extensions

The most common inheritance in automation: `class XTest extends BaseTest`. It starts as "share the setup"
and ends as a class with a browser, a DB, an API client, test data, screenshots and a drawer of helpers —
inherited by every test whether it needs them or not. JUnit 5 lets a test **compose** what it needs instead:
one extension per resource, meta-annotations to name common sets, parameters to receive the objects.

The JUnit 5 extension as a *composition root* (who creates and owns) is in [`coupling`](../../coupling/README.md);
scopes beyond "per test" are in [`parallel`](../../parallel/README.md). Here: **extends vs extension**.

| Package | What to look at |
|---|---|
| `bad/BaseTest` | everything in one `@BeforeEach`; `protected` fields; overridable `setUp()` / `userName()`; helpers drawer |
| `bad/split` | the "fix": `ApiBaseTest → UiBaseTest / DbBaseTest`; a test that needs UI + DB gets `UiDbBaseTest`, with the DB half copied |
| `good` | `ApiExtension` (`ApiClient`, `User`), `BrowserExtension`, `DbExtension`, `ScreenshotOnFailure`; meta-annotations `@ApiTest`, `@UiTest`, `@DbTest`; `Checks` — stateless helpers as static functions |
| `good/mixins` | JUnit *test interfaces*: `@ExtendWith` + `default` helper methods, no fields — multiple inheritance of behaviour |
| root | fakes `ApiClient`, `Db`, `Browser`; `Startups` — a probe log of what was started |

Run:
```
mvn -q -pl principles test -Dtest='BaseTestVsExtensionsTest'
```

`BaseTestVsExtensionsTest` runs small test classes through `EngineTestKit` and reads `Startups`:
- **bad** — an API-only test logs `api client, db connected, browser launched, …`; a subclass that declares its
  own `@BeforeEach protected void setUp()` *overrides* the base one, JUnit runs only the subclass version, and
  the test dies with NPE on `browser`; `UiDbBaseTest` is not a `DbBaseTest` and re-declares its methods.
- **good** — an API test logs only `api client`; `@UiTest @DbTest` composes without a new class and
  `ApiExtension` is registered once; a test's own `setUp()` shadows nothing; a failing UI test gets a
  screenshot *before* the browser is closed, a failing test without a browser does not launch one for it.

## Mapping

| `BaseTest` piece | Composition |
|---|---|
| `protected Browser browser` + launch in `setUp()` | `Browser` parameter, `BrowserExtension` launches lazily, Store closes |
| `protected User user` created for every test | `User` parameter from `ApiExtension`, named after the test |
| `userName()` hook to change the user | the test creates the user it is about: `api.createUser("admin")` |
| screenshot in `@AfterEach` / `TestWatcher` in the base class | `ScreenshotOnFailure`, cooperating via `BrowserExtension.started(ctx)` |
| `retry`, `randomName`, `assertStored` | static helpers / AssertJ custom assertions / `Checks` |
| `UiDbBaseTest` | `@UiTest @DbTest` |
| "all tests extend BaseTest" | a meta-annotation per test kind; a team-wide `@E2eTest` if you need one name |

## Pros / cons

| Pros | Cons |
|---|---|
| A test pays only for what it declares | More small classes; the wiring is spread over annotations |
| Signatures tell what the test uses | Parameter lists grow — 5+ parameters means the test does too much, or wants a domain facade |
| Any combination without a new class | Extension API is more ceremony than a `@BeforeEach` |
| Lifecycle is JUnit's: closed on failure, no `super.tearDown()` to forget | Cooperating extensions need an agreed Store key (`BrowserExtension.started`) |
| No fragile base: nothing to override | Field injection (`@Inject`-like) is tempting and brings hidden inputs back |

## When NOT to apply
- **A handful of tests of one kind** — one class with a `@BeforeEach` is simpler than three extensions.
- **An abstract base with only annotations** — `abstract class UiTestBase` annotated `@UiTest`, no fields, no
  methods: harmless. Inheritance of *annotations* is not the problem; inheritance of state and helpers is.
- **TestNG** — no extension model of JUnit's kind; listeners + `@Guice` / factories, or a thin base class with
  `final` lifecycle methods and hooks (see [`session`](../session/README.md)), is the pragmatic choice.
- **Test interfaces as a new drawer** — `good/mixins` is fine while interfaces stay small and stateless;
  ten `default` helpers in `UiAssertions` is `BaseTest` again.

## Try it yourself
1. Mark `BaseTest.setUp()` `final` (and `private`?). Which bug disappears, and what does a subclass that needs
   extra setup do now? Is `final` + hooks enough to make `BaseTest` good?
2. Add a "Kafka events" resource. Count what changes in `bad/split` (which branch? `UiKafkaDbBaseTest`?) and in `good`.
3. Write `@LoggedIn Browser browser`: a parameter annotation that makes `BrowserExtension` log in the test's
   `User`. Where does the knowledge "who is logged in" live — and is it still visible in the test?
4. Move `GoodUiAndDb` to field injection (`@Inject Browser browser`). What did you gain, what came back?
5. Smell check on your framework: count fields in your base test class, count classes that extend it but never
   touch half the fields, grep for `super.setUp()`.
