# ADR 0014 — JUnit 5 vs TestNG: one project, two runners; what the runner glue must do and how TestNG does it

Status: accepted (stage 5b, `project/testng`). The twin of `project/junit` (ADR 0012, 0013).

## Context

`project/junit` confined JUnit to the `assembly` package (an ArchUnit rule) so that a team could swap the runner by
rewriting one package. Stage 5b does it: `project/testng` is a copy whose `src/main` differs from the JUnit one only
in the runner glue, `project/check-twins.sh` checks that (CI runs it). Layers, the composition root
(`ProjectConfiguration`), the test scope (`assembly/scope`), configuration and the test bodies stay.

The JUnit glue (`ProjectExtension` + `@ProjectTest` + kind annotations) does seven things. Each has to be rebuilt from
TestNG parts, and three TestNG facts make it more than a translation:

1. **One instance of a test class runs all its methods**, in parallel under `parallel="methods"` (JUnit: an instance
   per method). A field is shared by concurrent tests (`principles/parallel` §8).
2. **No parameter resolution**: a test method gets only TestNG's own types (`ITestContext`, `Method`, `ITestResult`,
   …) or a data provider's row.
3. **No meta-annotations**: TestNG reads `@Test`, `@Listeners` where they are written; `@ApiTests` that carries a tag
   and the wiring cannot exist.

## Decision

| What the glue does | JUnit 5 (`project/junit`) | TestNG (`project/testng`) |
|---|---|---|
| Registration | `@ExtendWith` inside `@ProjectTest` inside the kind annotation | `ProjectListener` in `META-INF/services/org.testng.ITestNGListener`: active in every TestNG run (Surefire, IDE, programmatic), no `<listeners>` to forget |
| Kind of a test | annotation `@ApiTests` (meta: `@Tag` + `@ProjectTest`) | group: `@Test(groups = Groups.API)` on the class; ArchUnit: exactly one kind, none on methods |
| Spring context | `SpringExtension` (official) | the listener asks a `TestContextManager` of `ProjectContext` (a class with `@SpringBootTest`) for the cached context; no base class |
| Dependencies | `ParameterResolver`: any parameter whose type is a bean | `IAnnotationTransformer` gives a project test with parameters the data provider `project-beans` (one row of `null`s); `IHookable` fills the row with beans before the call |
| Test scope | `TestScope.within` while resolving, `TestBeans` in the extension store, closed in `afterEach` | the same `TestScope.within` while resolving, inside `IHookable.run` around the call on the test's thread; closed in its `finally` |
| Environment unavailable | `ExecutionCondition`: class disabled with the reason | `SkipException` with the reason (cached per class); failure under `project.require-environment=true` |
| Parallelism ≤ browsers | reads `junit.jupiter.execution.parallel.*` | reads the `<test>`'s `parallel` and `thread-count` |
| Screenshot on failure | `AfterTestExecutionCallback` (before `afterEach` closes the browser) | in the hook after the call, before the beans close (`onTestFailure` would be too late) |
| Retry in CI | Surefire `rerunFailingTestsCount` (Maven profile `ci`), flaky in the XML | `IRetryAnalyzer` (`ProjectRetry`) attached by the transformer when `rerun.count` > 0 (same Maven profile); the failed attempt is a "retried" skip; flaky tests listed in `target/flaky-tests.txt` |
| Parallel run, isolation | `junit-platform.properties`; `@Isolated` on a class | `testng.xml`: `<test parallel="methods" thread-count="4">`; group `isolated` + the class listed in a `<test>` of its own that runs first |
| Report | `allure-junit-platform` | `allure-testng` (ServiceLoader) |
| Surefire | JUnit Platform provider (auto) | `surefire-testng` named explicitly + `suiteXmlFiles` |
| ArchUnit | `archunit-junit5` engine, `@ArchTest` fields | `archunit` core: the same rule fields, one data-provider invocation per field |

Details that are decisions, not translations:

- **Own listener, not `AbstractTestNGSpringContextTests`.** The base class is Spring's only official TestNG support;
  it would make every test extend it (the JUnit twin forbids inheritance: "kinds are annotations, not base classes"),
  and it injects *fields* — exactly what fact 1 forbids. The listener uses the same public `TestContextManager` and
  only asks it for the context: Spring's test execution listeners (`@DirtiesContext`, `@MockitoBean` resets, `@Sql`)
  are not driven, which is fine because ArchitectureTest forbids Spring in tests (a second context is a second
  environment).
- **Parameters, not an accessor.** The alternative was a thread-bound accessor (`Beans.get(UserSteps.class)`) bound
  for the whole method. It is a service locator (a test can ask for anything anywhere), and it would bind the test to
  the thread for the whole body — the `ThreadLocal` pitfalls `TestScope` avoids. The data provider + hook keep the
  JUnit contract: a test lists what it needs, the test is bound only while resolving, another thread fails with the
  same message. The test bodies are the JUnit ones; only the kind annotation, `public` and imports differ.
  Costs: a project test cannot have its own data provider *and* bean parameters (the transformer leaves a method
  with its own provider alone); `onTestStart` empties the result's copy of the row, or Surefire would print
  `test[null, null](1)` and Allure `arg0 = null` — and it must run before Allure's listener (ServiceLoader finds the
  module's classes before the jars; TestNG keeps that order).
- **No fields in project tests** (ArchUnit, new rule): the scope cannot protect a field. Component tests that keep a
  fake in fields (`ContractFilterTest`, `TestScopeTest`, `ReportingTest`) say `@Test(singleThreaded = true)`: their
  methods run one by one, the class still runs next to others. No bean parameters on `@BeforeMethod`: TestNG
  validates configuration-method parameters itself, and what such a method set up could only reach the test through a
  field.
- **Native TestNG provider, not `testng-engine`.** The JUnit Platform's TestNG engine (used by the `principles/parallel`
  kata to run both in one Surefire run) reads `testng.*` configuration parameters but no suite file. The suite file is
  where TestNG keeps parallelism, isolation and the test set; a team on TestNG expects it. The parent pom puts JUnit
  Jupiter on every test class path, so the provider is named explicitly (auto-detection would choose the Platform),
  and ArchUnit forbids `org.junit` anywhere in the module: a JUnit `@Test` there compiles, is green in the IDE and
  never runs in the build.
- **Suite by package, recursively** (`com.study.project.*`): a new test class needs no edit of `testng.xml`. Two
  traps of TestNG's package scanning, both handled: it includes nested classes (the listener's fixtures live in
  `assembly.fixtures`, excluded by a package pattern — TestNG matches `<exclude>` against package names, not
  classes), and `-Dgroups` replaces the groups of *every* `<test>`, so the isolated `<test>` lists its class by name.

## Alternatives

| Option | Why not |
|---|---|
| `AbstractTestNGSpringContextTests` as the base of every test | Inheritance for wiring; field injection under `parallel="methods"` is a race; the twin's tests would differ in more than annotations |
| A thread-bound accessor for dependencies | Service locator; binds the test to the thread for the whole body (see above) |
| `@Guice` (TestNG's DI) | Injects the class instance (one per class), not one test; a second container next to Spring |
| `ThreadLocal` fields in tests (the usual TestNG answer) | Works only while every step stays on the test's thread; `principles/parallel` shows where it breaks |
| `testng-engine` on the JUnit Platform | No `testng.xml`; one Surefire provider for ArchUnit, but the TestNG way of configuring a run is gone |
| One `<test>` with groups for isolation | `-Dgroups` overrides it: the chosen kind would run twice |
| Listeners in `testng.xml` | An IDE run of one class would not have them: parameters stay `null`, the test scope is never opened |

## Consequences

- \+ The swap is real and checked: `src/main` differs in the glue classes only (four here: `Groups`,
  `ProjectContext`, `ProjectListener`, `ProjectRetry`; six there: `@ProjectTest`, `ProjectExtension` and four kind
  annotations) and the ServiceLoader file; `ProjectConfiguration`, `ProjectProperties` and `assembly/scope` are byte-identical.
  Test bodies are the same; the rules are the same plus two TestNG-specific ones.
- \+ Both projects end the same: 62 (JUnit) and 68 (TestNG: ArchUnit rules are counted per rule, the listener has
  more to prove) green tests on Testcontainers + Chromium, no skips.
- − The TestNG glue is bigger and uses four extension points (transformer, data provider, hook, test listener) for
  what JUnit offers as one interface (`ParameterResolver`) and its lifecycle callbacks; it depends on TestNG details
  (one hookable and one transformer per run, listener order, parameter arrays shared with the hook). A test class
  that implements `IHookable` itself replaces the project's hook for its methods: no beans, no scope.
- − Isolation and the test set are in two places (`testng.xml` and the class), not one annotation.
- − Retry is in-process: the retried attempt is a skipped result, not Surefire's `flakyFailure`; CI reads
  `flaky-tests.txt` instead.

## When to pick which

| Pick JUnit 5 when | Pick TestNG when |
|---|---|
| A new project: per-method instances, parameter injection, extensions that compose by meta-annotations | The organisation already runs TestNG: suites, groups, reports, IDE habits, CI scripts built on `testng.xml` |
| Spring Boot tests: `SpringExtension` is first-class, the TestNG integration is a base class | Data-driven suites (`@Factory`, `parallel="instances"`, `@DataProvider(parallel = true)`) are the main shape |
| Several engines in one run (jqwik, ArchUnit, Cucumber on the Platform) | Ordering and dependencies between tests (`dependsOnMethods`, `priority`) are wanted — knowingly |

Either way the decision is cheap only if the runner stays in one package: that is the rule worth copying, more than
the choice of runner.
