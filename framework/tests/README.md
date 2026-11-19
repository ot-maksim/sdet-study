# framework/tests

The assembly. `SutComposition` is the composition root: the one class that knows every module. Tests declare
what they need as parameters; there is no base class, no static state, and they run in parallel.

| Where | What to look at |
|---|---|
| `SutComposition` | every `new` of the framework in `bind`; RUN vs TEST lifetimes; the choices: precondition channel (`domain.user-precondition`), browser profile (`ui.profile`), screen implementation; stand down → skip or fail (`stand.required`); screenshot of a failed test that had a browser |
| `@ApiTests` / `@UiTests` / `@E2eTests` | test kinds as meta-annotations: a JUnit tag + the root |
| `env/local.properties` | the compose stand — the only file that knows its URLs and credentials |
| `junit-platform.properties` (test) | parallel execution, 4 threads (≤ `ui.max-browsers`) |
| tests | `UsersApiTest`, `UsersPageTest`, `UserJourneyE2eTest` (API → DB → page, page → API → DB) |
| `ArchitectureTest` | the layer rules as ArchUnit tests, no stand needed: core ← clients (not each other) ← domain ← tests; a test does not touch the transport (RestAssured, Playwright, JDBC, `db`, `ApiClient`, `UsersPage`, `Config.load`), does not `new` services (records only), does not extend a class, has exactly one kind |

## Run

```
mvn -q -pl sut -am package -DskipTests && docker compose up -d --build --wait
mvn -q -pl framework/tests -am test                       # all kinds, in parallel
mvn -q -pl framework/tests -am test -Dgroups=ui           # one kind
mvn -q -pl framework/tests -am test -Dui.profile=mobile -Ddomain.user-precondition=db
mvn -q -pl framework/tests -am test -Dstand.required=true # CI: a missing stand fails, not skips
mvn -q -pl framework/tests -am test -Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false  # layer rules only
```

Every key can also come from the environment (`UI_PROFILE=mobile`) or another stand file (`-Dframework.env=qa`
→ `env/qa.properties`). The resolved configuration is logged at the start of a run, secrets masked.

## What was checked

| Run | Result |
|---|---|
| default (desktop, API preconditions), parallel | 9/9 green; no rows left behind |
| `ui.profile=mobile`, `domain.user-precondition=db` | 9/9 green — no test changed |
| `ui.timeout=1ms` | UI tests fail; a screenshot per failed test in `target/screenshots/`; still no rows left behind |
| stand down | all skipped with the reason and the command to start it |
| stand down + `stand.required=true` | fails with the same reason |
| `ArchitectureTest` with a planted test (extends a class, no kind, `new Unique()`, `Config.load()`, `DriverManager`) and a `core` class holding `UserSteps` | 5/5 rules fail, each naming the class, the line and the reason |

Finding: ArchUnit reads bytecode, so a type used only by an unused local variable (`ApiClient client = null;`)
is invisible to it; a call, a field, a parameter or a `new` is not.

Finding: the screenshot after a timeout failed with the same timeout — taking evidence after a failure needs its
own timeout, and its own failure must not replace the test's.

## How a test reads

```java
@E2eTests
class UserJourneyE2eTest {
    @Test
    void apiToDatabaseToPage(UserSteps users, UserChecks checks, UsersScreen screen) {
        User ada = users.registerThroughApi(users.aNewUser().withCountry("it"));

        checks.assertStoredAsReturned(ada);
        checks.assertListed(screen, ada);
    }
}
```

No setup, no teardown, no knowledge of HTTP, SQL, selectors, browsers or the stand.

## Decisions

[ADR 0007 — test assembly: test kinds as annotations, stand policy, parallel by default](../../docs/adr/0007-test-assembly.md);
the root itself — [ADR 0001](../../docs/adr/0001-composition-root-by-hand.md).

## Try it yourself

1. Bind `UsersScreen` to a decorator that logs every call (`LoggingUsersScreen implements UsersScreen`). How many files change?
2. Ask for `UsersScreen` in a `@BeforeAll` method. Read the message.
3. Raise `fixed.parallelism` to 8 with `ui.max-browsers=4`: what happens to the UI tests, and why do they not fail?
4. Take `UsersPage` as a test parameter instead of `UsersScreen`. Which rule fails, and what would break first without it?
5. Add a rule to `ArchitectureTest`: no mutable static field in the main code of any layer. Which class does it
   find, and is that one a bug or a decision worth a `because`?
