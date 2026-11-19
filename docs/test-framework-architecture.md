# What makes a test framework architecture good

A reference note. It describes a test framework by the **properties it guarantees** and the **change scenarios
that check them**, not by the patterns it uses: "we have Page Object, Builder and DI" says nothing about whether
the suite survives its second year. Patterns are the means; properties are what a team can agree on, measure and
argue about. Every property below links to the place in this repo where it is shown.

## Definition

> A good test framework architecture keeps the **cost of adding a test, surviving a change in the SUT and
> understanding a failure** low and predictable **as the suite grows**.

"As the suite grows" is the point. Any structure works for 20 tests; the difference shows at 2000 tests, a second
team and the third stand.

## How to describe it: property → scenario → check

A property is only useful when it has a concrete scenario and a way to check it; then it is a criterion, not an
opinion (an architectural *fitness function*). The decisions behind each one go to ADRs ([`adr/`](adr/)).

| Property | Scenario | Check | In this repo |
|---|---|---|---|
| **Expressiveness** | A test reads as a specification of behaviour | Test bodies speak the domain; tests never touch the transport (HTTP, SQL, locators) | [`framework/tests/.../UsersApiTest`](../framework/tests/src/test/java/com/study/framework/tests/UsersApiTest.java): `users.aNewUser().withAge(17)`, `isRejectedWith(400, "age_too_low")`, `checks.assertNotStored(...)` |
| **Locality of change** | An endpoint is renamed, a locator changes, a field becomes required, the assembly changes | One edit in one place (client, page object, builder default, root); zero edits in tests | The test bodies in [`framework/tests`](../framework/tests/src/test/java/com/study/framework/tests/UsersApiTest.java) and [`template`](../template/src/test/java/com/study/template/UsersApiTest.java) are identical: hand-made DI → Spring, compose stand → Testcontainers, plus Allure, and no test changed |
| **Isolation and determinism** | A test runs alone, in any order, next to others on a shared stand | It creates its own data and removes only that; it finds its data by key, never by position or count | [`Unique`](../framework/core/src/main/java/com/study/framework/core/data/Unique.java) (run id + counter), [`Cleanup`](../framework/core/src/main/java/com/study/framework/core/lifecycle/Cleanup.java) ("register the undo right after the do"), [`UsersScreen`](../framework/ui/src/main/java/com/study/framework/ui/users/UsersScreen.java) (find by email) |
| **Parallelizability** | Parallel execution is switched on | No mutable static state; every dependency has an owner and a lifetime (run or test); the result equals the sequential one | [`SutComposition`](../framework/tests/src/main/java/com/study/framework/tests/SutComposition.java) (RUN vs TEST scopes), [`BrowserPool`](../framework/ui/src/main/java/com/study/framework/ui/BrowserPool.java) (a browser is leased, never shared), [`ParallelUiTest`](../framework/ui/src/test/java/com/study/framework/ui/users/ParallelUiTest.java) (checks the tests really overlapped), [`CaptiveDependencyGuard`](../template/src/main/java/com/study/template/scope/CaptiveDependencyGuard.java), parallel guard in ADR [0011](adr/0011-report-retry-ci.md) |
| **Diagnosability** | A test fails at night | The report alone says what broke, where, and whose it is (product vs infrastructure), without a rerun or a debugger | [`ApiResponseAssert`](../framework/api/src/main/java/com/study/framework/api/ApiResponseAssert.java) and [`ApiException`](../framework/api/src/main/java/com/study/framework/api/ApiException.java) carry the whole exchange; stand probe in [`SutComposition`](../framework/tests/src/main/java/com/study/framework/tests/SutComposition.java) (stand down → skip or fail with the reason, not 50 red tests); screenshot on failure; Allure steps by [decorator](../template/src/main/java/com/study/template/report/ReportingUsersScreen.java) and [aspect](../template/src/main/java/com/study/template/report/ReportingAspect.java) |
| **Trustworthy signal** | A test fails once in twenty runs | A flake is visible and costs nothing locally; a retry never hides it | Retry only in CI, only once, reported as `Flaky` in the job summary ([ADR 0011](adr/0011-report-retry-ci.md), [`tests.yml`](../.github/workflows/tests.yml)) |
| **Environment independence** | Move from one stand to another, or to containers | A config value or profile changes; tests and clients do not | Layered [`Config`](../framework/core/src/main/java/com/study/framework/core/config/Config.java) (unknown key fails the run, [`Secret`](../framework/core/src/main/java/com/study/framework/core/config/Secret.java) is masked), [`EnvironmentProvider`](../template/src/main/java/com/study/template/environment/EnvironmentProvider.java): `stand` \| `containers` |
| **Extensibility** | A new channel (Kafka, native mobile) or a new way to prepare data | Existing layers are only added to, never edited; the new implementation passes the same contract test | [`UserPrecondition`](../framework/domain/src/main/java/com/study/framework/domain/UserPrecondition.java) (API or DB) + [`UserPreconditionContract`](../framework/domain/src/test/java/com/study/framework/domain/UserPreconditionContract.java); [`UsersScreen`](../framework/ui/src/main/java/com/study/framework/ui/users/UsersScreen.java) + `UsersScreenContract` (desktop, mobile web; native next); events in [ADR 0010](adr/0010-events-kafka.md) |
| **Low entry cost** | A developer writes their first test | Done without help from the framework's authors: one annotation, parameters, domain calls | A test kind is an annotation ([`@ApiTests`](../framework/tests/src/main/java/com/study/framework/tests/ApiTests.java)), dependencies are test parameters; not checked here yet (see *Gaps*) |

## Structural rules that give these properties

1. **Layers with one-way dependencies**: tests → domain (steps, preconditions, checks) → clients (api, ui, db) →
   core. Tests do not see the transport. Here the rule is enforced by Maven modules: a forbidden import is a
   compile error, not a review comment ([ADR 0006](adr/0006-domain-layer.md), [0007](adr/0007-test-assembly.md)).
2. **One composition root.** Every `new` of the framework is in one place; tests only declare what they need.
   No singletons, no service locator in tests ([ADR 0001](adr/0001-composition-root-by-hand.md),
   [`SutComposition`](../framework/tests/src/main/java/com/study/framework/tests/SutComposition.java); the same role
   in Spring: [ADR 0008](adr/0008-spring-test-context.md)).
3. **Explicit lifetimes.** Each object is run-scoped (immutable or thread-safe) or test-scoped (closed after the
   test, also on failure). A run object never holds a test object (the captive dependency).
4. **Ports for everything external**: environment, time, the way data is prepared, every channel to the SUT. A
   port is what makes a double, a second implementation and a contract test possible
   ([ports-and-adapters.md](ports-and-adapters.md), [test-doubles.md](test-doubles.md)).
5. **Test data**: builders with valid, unique defaults; a test states only what differs
   (`aNewUser().withAge(17)`, [`NewUsers`](../framework/api/src/main/java/com/study/framework/api/users/NewUsers.java)).
   Cleanup is registered by whoever created the data.
6. **The oracle is independent of the SUT.** The framework does not import the SUT's DTOs, mappers or rules: an
   expectation computed by the SUT's own code is a tautology ([`review` P10](../review/REVIEW.md)).
7. **Assertions at the domain level with context**: the failure message is the first line of the investigation.
8. **Cross-cutting concerns by observers, extensions and decorators**, not by a base class: report, screenshots,
   retry, stand checks are properties of the assembly, and the layers do not know the report tool
   ([ADR 0011](adr/0011-report-retry-ci.md)).
9. **One typed configuration** per run, validated at start, read into settings records by the module that owns
   the keys ([ADR 0002](adr/0002-layered-config.md)).

## Smells: the same properties, broken

Each of these breaks one or more properties above. The planted versions, with tests that show the damage, are in
[`review/`](../review/REVIEW.md); a grep-able list is in [`principles/SMELLS.md`](../principles/SMELLS.md).

| Smell | Breaks | In `review/` |
|---|---|---|
| `BaseTest` that starts every resource for every test | entry cost, speed, diagnosability | P01 |
| Static driver / client / `RestAssured.baseURI` | parallelizability, isolation | P02 |
| Config singleton with silent defaults | environment independence, diagnosability | P03 |
| "Random" data from a small space, cleanup by prefix | isolation, trustworthy signal | P04, P05 |
| Client returning a raw `Response`, no status check at the call | diagnosability | P06 |
| Page object that asserts, sleeps and prepares data | locality of change, speed | P07 |
| Shared static `ScenarioContext` between steps | parallelizability | P09 |
| Framework built from the SUT's own classes | trustworthy signal (green test that lies) | P10 |
| Locators, URLs, JSON in test bodies; `Utils` of 2000 lines | expressiveness, locality of change | — |
| Retry on everything, locally too | trustworthy signal | — |

## When not to apply the whole thing

Every layer is an indirection and a cost of onboarding. For 30 tests on a short-lived project, or a one-off
migration check, a full stack of layers is over-engineering. The criterion is the same as above: design for the
**expected** growth and the **expected** changes. Introduce a layer when its scenario actually appears (the
second channel, the second stand, parallel runs), not "just in case". An abstraction over a single
implementation with no second one in sight is a smell of its own
([`principles/kiss`](../principles/src/main/java/com/study/principles/kiss/README.md), `yagni/`: the interface
extracted from two real consumers, not one planned; decoy D1 in [`review/`](../review/REVIEW.md)).

## How to use this note

- **Reviewing a framework** (inherited or your own): walk the table and ask for each scenario "show me the edit".
  An answer of "in every test" or "it depends on the order" is the finding.
- **An ADR**: name the property it serves and the scenario that would break without it.
- **An interview**: "here are three scenarios, how does your framework handle them?" tells more than "which
  patterns do you use".

## Gaps in this repo (try it yourself)

Properties that are shown here but not yet checked automatically:

1. **Layer rules as tests.** Maven modules forbid wrong *module* dependencies; rules inside a module (a test
   class never imports `io.restassured` or `com.microsoft.playwright`) are not checked. ArchUnit would make them
   a failing test.
2. **Order independence.** Isolation is shown by parallel runs, not by a random method and class order
   (`junit.jupiter.testmethod.order.default=...MethodOrderer$Random`) in CI.
3. **Entry cost.** No measurement: give the framework to a developer who has not seen it, write down every place
   they had to ask, and treat each as a defect of the framework's API.
4. **Locality of change as a metric.** Count files touched per SUT change over the last N commits; a growing
   number of test files per change is the earliest sign of eroding layers.
