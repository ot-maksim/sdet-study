# template — stage 4a: Spring Boot test context + Testcontainers

The working template. The stage 2 layers (`framework/core`, `api`, `db`, `ui`, `domain`) are reused **as they are**;
only the assembly is new: a Spring Boot test context instead of the hand-written root, and an environment that is
either a running stand or started by the run itself with Testcontainers.

The point to see: the three test classes of `framework/tests` moved here with one changed line (`package`).
Nothing below the assembly knows about Spring, Docker or which environment ran.

| Where | What to look at |
|---|---|
| `TemplateConfiguration` | the root, Spring edition: one `@Bean` per binding, no scanning, no auto-configuration; `@TestScoped` beans; the two choices (environment, precondition channel) as `switch` on enums |
| `TemplateProperties` + `application*.yml` | every key, typed records; `ignoreUnknownFields = false` (a typo fails); profiles `stand`, `ci`, `mobile`, `db` as slices that combine |
| `scope/TestScope` | the test scope Spring does not have; bound to the thread only while a test's parameters are resolved |
| `scope/CaptiveDependencyGuard` | a singleton holding a test bean fails at creation, as in stage 2 |
| `TemplateExtension` | parameters without `@Autowired`; skip or fail when the environment is unavailable; screenshot of a failed UI test |
| `environment/` | `EnvironmentProvider` seam; `StandEnvironment` (connect + probe), `ContainersEnvironment` (Postgres + SUT image, private network, random ports, generated password) |
| `@ApiTests` / `@UiTests` / `@E2eTests` → `@TemplateTest` | test kinds: a tag + `@SpringBootTest` + the extension; one cached context for the run |
| tests | `UsersApiTest`, `UsersPageTest`, `UserJourneyE2eTest` — copies of stage 2; `scope/TestScopeTest`, `TemplatePropertiesTest`, `TemplateExtensionTest` — the mechanics |

## Run

```
mvn -q -pl template -am verify                                    # hermetic: needs only Docker
mvn -q -pl template -am verify -Dgroups=ui                        # one kind
mvn -q -pl template -am verify -Dspring.profiles.active=mobile,db # same tests, phone context, DB preconditions
mvn -q -pl template -am verify -Dspring.profiles.active=stand     # against docker compose (see sut/README.md)
mvn -q -pl template -am verify -Dspring.profiles.active=ci        # unavailable environment fails, not skips
```

Any key also from the environment: `TEMPLATE_UI_PROFILE=mobile`, `SPRING_PROFILES_ACTIVE=stand`.
`-am` matters for the containers mode: it packages `sut/target/sut.jar`, which the image is built from.

## Stage 2 → stage 4a

| Stage 2 (`framework/tests`, ADR 0001, 0002) | Here (ADR 0008, 0009) |
|---|---|
| `b.run(X.class, c -> new X(c.get(Y.class)))` | `@Bean X x(Y y) { return new X(y); }` |
| `b.test(...)`, TEST lifetime from the JUnit store | `@Bean @TestScoped`, custom `TestScope` |
| `c.cleanup().own(resource)` | the bean's destroy method (`close` is inferred), registered with the test scope |
| RUN objects built lazily | `spring.main.lazy-initialization: true` |
| one run scope per root, `static`-free | Spring's context cache: one context per configuration per JVM |
| `Config`: sysprop → env → `env/<env>.properties` → `framework.properties`; catalogue catches typos | Spring Boot: sysprop → env → `application-<profile>.yml` → `application.yml`; `ignoreUnknownFields = false` |
| `Secret` from `ConfigReader.secret` | `Secret` bound by a `@ConfigurationPropertiesBinding` converter |
| captive dependency, TEST in `@BeforeAll`, cycles — refused by the root | captive — `CaptiveDependencyGuard`; `@BeforeAll` / other thread — `TestScope`; cycles — Spring itself |
| parameters resolved by the root | `TemplateExtension` (Spring alone needs `@Autowired` on each test parameter) |
| the stand is given (`docker compose`) | `EnvironmentProvider`: `stand` or `containers` (default) |

## What was checked

| Run | Result |
|---|---|
| default (containers), parallel | 9/9 green; environment up in ~6 s (Postgres ~1 s, image build ~3 s, SUT ~1 s); whole module ~30 s |
| `stand` profile against compose | 9/9 green; rows in `users` before = after (cleanup works for both modes) |
| `mobile,db` on containers | 9/9 green — no test changed |
| `stand` + `template.ui.timeout=1ms` | UI tests fail, one screenshot per failed test in `target/screenshots/`, still no rows left |
| no `sut.jar` (`-Dtemplate.containers.sut-build-dir=/nowhere`) | skipped: "No /nowhere/target/sut.jar to build the SUT image from: mvn -q -pl sut -am package -DskipTests" |
| the same with profile `ci` | fails with that reason |
| mechanics (no Docker needed) | `TestScopeTest` 5, `TemplatePropertiesTest` 5, `TemplateExtensionTest` 5 |

Findings:
- Spring infers a destroy method from the **object**, not the declared type: the `Cleanup` bean is the test's own
  `Scope` and would have been registered to close itself — `@Bean(destroyMethod = "")`.
- `@SpringBootTest` resolves a test method parameter only with `@Autowired`; without our resolver, the stage 2 tests
  do not run unchanged.
- An exception from an `ExecutionCondition` reaches the report wrapped ("Failed to evaluate condition [...]: ");
  the reason is still the first thing after it.
- `commons-logging` from RestAssured's httpclient next to Spring's `spring-jcl` prints a warning on every start;
  excluded in this module only.
- Testcontainers 1.21.4 (managed by Boot 3.5.16) works with Docker Engine 29; the first Ryuk start once took 30 s,
  later ones ~0.4 s.

## Pros and cons

| + | − |
|---|---|
| Profiles, env vars, typed binding, integrations expect Spring — less own code to maintain | Three Spring gaps closed by hand (test scope, parameters, destroy inference); someone must own those classes |
| The graph is still in one class, readable top to bottom | `@Bean` methods look like plain code, but lazy init, scopes and inferred destroy are behaviour you do not see |
| Hermetic by default: a clean machine with Docker runs everything | Docker everywhere; ~6 s before the first test; a stale jar is a stale SUT |
| One environment per run without static state (context cache) | Any per-class configuration change silently starts a second context and environment |

## When NOT to apply

- A small suite against one fixed stand: the hand-written root (stage 2) is less to learn and has no gaps to close.
- Component scanning "to save the `@Bean` methods": the saving is lines, the cost is a graph nobody can read.
- Testcontainers when the environment is not yours to start (a shared UAT with real integrations): the stand mode
  exists for that; a container copy of a third-party system is a different test.
- Per-class `@Container` fields for an E2E suite: a database per class multiplies start-up and hides cross-test
  data problems that the shared stand will show.

## Try it yourself

1. Add `@Autowired UserSteps users;` as a field of `UsersApiTest` and run it. Read the message; explain why a field
   is the wrong place in both stages.
2. Make `UserChecks` depend on `Cleanup` in `TemplateConfiguration`. Which class fails, when, with what message?
   Now remove `CaptiveDependencyGuard` from `TestScopeConfigurer` and run the suite in parallel: what goes wrong, and
   how long until someone would notice?
3. Put `@TestPropertySource(properties = "template.ui.headless=false")` on `UsersPageTest` and run the module. Count
   the Postgres containers that start (`docker ps` during the run) and explain the time difference.
4. Add a third environment: `ComposeEnvironment` with Testcontainers' `ComposeContainer` over `docker-compose.yml`.
   What has to change outside `environment/`? What collides with a developer's own stand, and how would you avoid it?
5. Replace the `switch` in `environmentProvider` with two `@Bean` methods and `@ConditionalOnProperty`. Mistype the
   value (`contaners`) and compare the error with the enum version.

## Decisions

[ADR 0008 — Spring Boot test context as the composition root](../docs/adr/0008-spring-test-context.md),
[ADR 0009 — environment provider: a running stand or Testcontainers](../docs/adr/0009-environment-provider.md).
