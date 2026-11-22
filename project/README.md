# project — stage 5: the working test project as a team would start it

What a team would start a real automation repository with, for the same SUT and stack as `template/`: UI
(Playwright, desktop and mobile web), API (RestAssured), DB (JDBC), events (Kafka), Spring Boot test context,
Testcontainers, Allure. Written twice, once per test runner, as two separate repositories would hold it:

| Project | Runner | README |
|---|---|---|
| [`junit/`](junit) (`project-junit`) | JUnit 5: `SpringExtension` + `ProjectExtension`, kinds as meta-annotations (`@ApiTests`) | [junit/README.md](junit/README.md) |
| [`testng/`](testng) (`project-testng`) | TestNG: one `ProjectListener` (ServiceLoader), kinds as groups, `testng.xml` | [testng/README.md](testng/README.md) |

Both pass the same 4 test classes (API, UI, E2E, events) on Testcontainers + Chromium, no skips.

## What stage 5 adds to `template/`

| Decision | What | ADR |
|---|---|---|
| One module, layers as packages | no Maven module per layer; `ArchitectureTest` enforces the dependency direction and where each tool may appear | [0012](../docs/adr/0012-module-granularity.md) |
| Client from the contract | operations and models generated from the SUT's `sut/src/main/resources/openapi.yaml`, wrapped by hand (`ApiTransport`, `UserApi`); every response validated against the same file (`ContractFilter`) | [0013](../docs/adr/0013-openapi-client.md) |
| The runner is assembly glue | JUnit or TestNG only in `assembly/`; the twins differ there and nowhere else in `src/main` | [0014](../docs/adr/0014-junit-vs-testng.md) |

## Layout of each project

```
com.study.project
├── core          Secret, Unique, Cleanup / Scope                      (no tools)
├── api           ApiTransport, ContractFilter, UserApi; generated/ in target/generated-sources
├── db            Database, UserRows                                   (JDBC)
├── ui            BrowserPool, UiSession, UsersPage                    (Playwright)
├── events        EventClient, UserEvents                              (Kafka)
├── domain        UserSteps, UserPrecondition (api | db), UserChecks
├── environment   EnvironmentProvider: stand | containers              (Testcontainers)
├── report        decorators, ApiReport filter, artifacts              (Allure)
└── assembly      ProjectConfiguration (the root), ProjectProperties, scope/ — shared by both twins
                  + the runner glue — the only part that differs
src/test: tests/ (UsersApiTest, UsersPageTest, UserJourneyE2eTest, UserEventsE2eTest) + component tests + ArchitectureTest
```

## Rules of the pair

- **Standalone.** Neither project depends on `framework/`, `template/` or its twin; they share only the SUT's contract
  and the SUT image. Code is copied, not shared, on purpose: two "repositories".
- **Everything but the runner glue is identical.** [`check-twins.sh`](check-twins.sh) compares `src/main` of both and
  fails if anything other than the top-level `assembly/*.java` and `META-INF/services` differs (a CI step). A change in
  a layer, the root, the configuration or the test scope goes to both projects.

## Run

```
mvn -q -pl sut -am package -DskipTests                                # once: the SUT image is built from sut/target/sut.jar
mvn -q -pl project/junit,project/testng verify                        # both twins, hermetic (Docker + Chromium)
mvn -q -pl project/testng verify -Dgroups=api                         # one kind: api, ui, e2e, events
mvn -q -pl project/junit verify -Dspring.profiles.active=stand        # against the compose stand (sut/README.md)
project/check-twins.sh                                                # what differs between the twins
```

More options (profiles `mobile`, `db`, `ci`, Allure) are in each project's README.

## Where to start reading

1. [`docs/adr/0012`](../docs/adr/0012-module-granularity.md) — why one module.
2. [`junit/README.md`](junit/README.md) — the project, what changed against `template/`, the contract check.
3. [`docs/adr/0014`](../docs/adr/0014-junit-vs-testng.md) and [`testng/README.md`](testng/README.md) — the same project
   on TestNG, a side-by-side table, when to pick which runner.
