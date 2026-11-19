# framework/core

The part of the framework that knows no client library and no SUT: configuration, the lifecycle of what tests
create, the mechanics of a hand-written composition root, unique test data. Every other layer depends on it;
it depends on nothing but the JUnit API.

| Package | What to look at |
|---|---|
| `config` | `Config` — layered (system property → env variable → `env/<env>.properties` → `framework.properties`), the defaults files are the catalogue of keys (a typo in an env file fails the run); `ConfigReader` — typed reads that collect every problem; `Secret` — masked `toString`; `describe()` prints each key with value and origin |
| `lifecycle` | `Cleanup` — the role a step needs ("register the undo right after the do"); `Scope` — runs undo actions in reverse order, all of them, reports every failure, refuses late registration |
| `inject` | `CompositionRoot` — a JUnit 5 `ParameterResolver` with one hook, `bind(Bindings)`; lifetimes `RUN` / `TEST`; lazy creation; guards: test object in `@BeforeAll`, captive dependency, cycle |
| `data` | `Unique` — `ada-k3x9q-7@example.test`: run id + counter, readable, safe on a shared stand |

Tests: `mvn -q -pl framework/core test`. `CompositionRootTest` runs small test classes through EngineTestKit and
checks scopes, closing order (also after a failed test) and the refused wirings by their messages.

## How the layers will use it

```java
// api module: its keys in its own framework.properties, its settings record
record ApiSettings(URI baseUrl, Duration timeout) {
    static ApiSettings from(ConfigReader r) { return new ApiSettings(r.uri("sut.base-url"), r.duration("api.timeout")); }
}

// tests module: the composition root — the only class that knows every module
public final class SutComposition extends CompositionRoot {
    protected void bind(Bindings b) {
        b.run(ApiSettings.class, c -> c.get(Config.class).bind(ApiSettings::from));
        b.run(UserApi.class, c -> new UserApi(c.get(ApiSettings.class)));
        b.test(UserSteps.class, c -> new UserSteps(c.get(UserApi.class), c.get(Unique.class), c.get(Cleanup.class)));
    }
}

// a test: declares what it needs; no base class, no statics
@SutTest
class UsersApiTest {
    @Test
    void createsAUser(UserSteps users) { ... }
}
```

## Decisions

- [ADR 0001 — composition root by hand, mechanics in core](../../docs/adr/0001-composition-root-by-hand.md)
- [ADR 0002 — layered configuration with a key catalogue](../../docs/adr/0002-layered-config.md)

## Pros / cons

| + | − |
|---|---|
| Every `new` is in one method; wiring is plain Java, refactorable, no magic | A type → factory table is a small container; it stays honest only while it lives in the root and nothing else calls `get` |
| Lazy creation per scope: an API test never starts a browser | Exact type matching: no "inject all implementations", no qualifiers — add them only when a real case appears |
| Lifetimes and their mistakes are explicit and tested | One lock per scope: run-scoped creation is serialized (fine for a few heavy objects, not for hundreds) |
| Config problems all at once, with origin; secrets masked by type | Env vars and system properties cannot be checked for typos — only the files can |

## When NOT to do this

- A dozen tests against one service: a `static final` client and a properties file are enough (KISS).
- A team already on Spring: use its test context and `@Value`/`@ConfigurationProperties` — stage 4 compares.
- Do not let steps or page objects take `Components` and call `get` — that turns the root into a service
  locator (see [`principles/coupling`](../../principles/src/main/java/com/study/principles/coupling/README.md)).

## Try it yourself

1. Add `ui.headless` to the test `framework.properties` and read it with `bool`; set `UI_HEADLESS=yes` — what is the message?
2. Bind a RUN-scoped `Reporter` that takes `Cleanup` — read the error, then decide: per test, or per call?
3. Make `Unique` a `static` utility. How does a test that needs a fixed, known id (a snapshot of a page) get one now?
