# review — stage 3: review of an inherited test framework

**The situation.** You join a team and inherit its test framework for the users service (`sut/`): a base class,
a driver holder, a config singleton, an API client, a page object, DB helpers, steps. The team's tests are green
"most of the time". Your task: review it as an architect — what will hurt, in what order to fix it — before you
read the answer key.

The framework has **10 planted problems** and **3 decoys** (code that a grep pass flags but that is right).
Each problem is shown by a test against the real SUT, and fixed in `good/` — a compact refactoring; the full
version of each fix is the stage 2 skeleton in [`framework/`](../framework).

| Where | What is there |
|---|---|
| `src/main/.../bad` | the inherited framework: `config/FrameworkConfig`, `core/BaseTest`, `core/Driver`, `core/ScreenshotOnFailure`, `api/UserApi`, `ui/UsersPage`, `db/DbUtils`, `data/TestData`, `steps/UserSteps`, `steps/ScenarioContext`, `steps/Expected`; `resources/config.properties` |
| `src/test/.../bad/suite` | the team's tests written on it (`UsersApiTest`, `UsersUiTest`) — review material, not run by Surefire |
| `src/main/.../good` | the refactoring: `config/Settings`, `api/UsersClient` + `ApiResponse` + own DTOs, `data/TestUsers`, `data/Cleanup`, `steps/UserSteps`, `ui/UsersPage`, `db/UserRows` |
| `src/test/.../good` | `GoodRoot` — the composition root (JUnit extension); `suite/` — the same stories on the refactored framework |
| `src/test/.../P01…P10*ReviewTest`, `bad/core/DecoysReviewTest` | one test class per problem: `Bad` makes the problem visible, `Good` shows it gone |
| [`REVIEW.md`](REVIEW.md) | **the answer key**: each problem — where, symptom, why it hurts, the test, the fix, the kata; the decoys; the order of refactoring |

## How to do the review (before REVIEW.md)

1. Time-box 45 minutes. Read `bad/suite` first (what the users of the framework see), then `bad/` top-down from
   `BaseTest`.
2. For every finding write: **where** (class:line), **what breaks and when** (a concrete scenario, not a
   principle's name), **severity** (wrong result > flaky > slow > hard to change), **the fix in one line**.
3. Ask the lifetime questions for each object: who creates it, who closes it, who else sees it, what happens in
   parallel. Most framework bugs live there, not in method bodies.
4. Run the grep pass from [`principles/SMELLS.md`](../principles/SMELLS.md) §9 and sort the hits: problem, decoy, or
   noise. Several problems here give no grep hit at all.
5. Then open [`REVIEW.md`](REVIEW.md) and score: found / missed / false alarms (a decoy reported as a problem).

## Run

```
mvn -q -pl review -am test                                   # all review tests and the good suite
mvn -q -pl review test -Dtest='P07*'                         # one problem
docker compose up -d --wait                                  # P08 (SQL) needs the compose Postgres + SUT, else skipped
```

The SUT runs in-process (in-memory storage, free port). The bad framework is pointed at it by system properties
before its config singleton is first used (`Stand.forBadFramework`) — its database deliberately unreachable, so
the tests show who depends on it. Chromium must be installed (`framework/ui/README.md`).

## What the tests show

| # | Problem | Bad (what the test sees) | Good |
|---|---|---|---|
| 1 | `BaseTest` god class | 3 API tests never start: `@BeforeAll` launched a browser, then died on JDBC; `@AfterAll` NPE | an API test runs, no browser launched |
| 2 | static `RestAssured.baseURI` / driver / connection | two parallel classes for two stands: both green, both users on stand two | each client keeps its stand |
| 3 | config singleton | file says `ui.timeout.ms=10000`, code reads `ui.timeout` → 30 s; a later `-D` ignored; password printed | typo / missing / partial DB → one error with all problems; secret masked |
| 4 | `Random.nextInt(1000)` emails | 200 users → duplicates (409 in a real run) | 10 000 unique; another run differs |
| 5 | cleanup by prefix | my `@AfterEach` deletes a parallel test's user; a user without the prefix stays forever | each test removes what it registered, also after a failure |
| 6 | raw `Response`, unchecked status | a rejected create passes; two steps later — NPE without 400/`age_too_low` | `create` fails at the call with the exchange; `tryCreate` for negatives |
| 7 | asserting, sleeping page object | a negative UI case is an `AssertionError` inside the page; ≥ 3 s of sleeps; `verifyUserInTable` passes for a user that was **not** created | `rejectedWith` → `age_too_low`; no sleeps |
| 8 | SQL concatenation | `o'brien@example.com` (valid for the SUT) breaks the query; `' OR '1'='1` "finds" a row | bound parameters, typed row, `Optional` |
| 9 | static `ScenarioContext` | scenario A checks scenario B's user; a step out of order → NPE | values are returned and held in locals |
| 10 | imports of the SUT | the oracle is `UserRules.validate` — the expectation is the code under test | no `com.study.sut` import in `good/` (architecture test) |
