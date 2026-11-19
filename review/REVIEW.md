# Answer key: 10 problems, 3 decoys

Paths are relative to `src/main/java/com/study/review/bad`. Severity: **wrong result** (a green test that lies,
or destroyed data) > **flaky** > **slow / costly** > **hard to change**. "Grep" says whether the pass from
[`SMELLS.md` §9](../principles/SMELLS.md) finds it — several of the worst ones are invisible to it.

| # | Problem | Where | Severity | Grep | Test | Kata |
|---|---|---|---|---|---|---|
| 1 | God base class: every test starts browser + DB + API | `core/BaseTest.java:26-35` | slow, flaky | no | `P01` | [`inheritance/basetest`](../principles/src/main/java/com/study/principles/inheritance/README.md) |
| 2 | Static shared state: `RestAssured.baseURI`, `Driver`, `BaseTest.db` | `core/BaseTest.java:20-31`, `core/Driver.java:14-17` | wrong result | yes | `P02` | [`parallel`](../principles/src/main/java/com/study/principles/parallel/README.md), [`coupling`](../principles/src/main/java/com/study/principles/coupling/README.md) |
| 3 | Config singleton: silent defaults, frozen at first use, secrets printed | `config/FrameworkConfig.java`, `core/Driver.java:30` | hard to change, leak | partly | `P03` | [`encapsulation`](../principles/src/main/java/com/study/principles/encapsulation/README.md) |
| 4 | Test data from 1000 values | `data/TestData.java:17` | flaky | no | `P04` | [`parallel`](../principles/src/main/java/com/study/principles/parallel/README.md) (isolation by data) |
| 5 | Cleanup by prefix | `data/TestData.java:26-35`, `core/BaseTest.java:38` | wrong result (others' data) | no | `P05` | [`kiss`](../principles/src/main/java/com/study/principles/kiss/README.md) (cleanup registry), `framework/core` `Scope` |
| 6 | Raw `Response`, no status check at the call | `api/UserApi.java`, `steps/UserSteps.java:21-26,46` | slow to diagnose | no | `P06` | [`deep`](../principles/src/main/java/com/study/principles/deep/README.md) |
| 7 | Page object asserts, sleeps, prepares data; check on `page.content()` | `ui/UsersPage.java:24-49` | wrong result, slow | `Thread.sleep` only | `P07` | [`srp`](../principles/src/main/java/com/study/principles/srp/README.md), [`concurrency/events/ui`](../principles/src/main/java/com/study/principles/concurrency/README.md) |
| 8 | SQL by concatenation, `Map` rows, leaked statements | `db/DbUtils.java:17-34` | wrong result, flaky | no | `P08` | `framework/db` (ADR 0004) |
| 9 | Steps share a static `ScenarioContext` | `steps/ScenarioContext.java`, `steps/UserSteps.java` | wrong result in parallel | `static … Map<` | `P09` | [`deep`](../principles/src/main/java/com/study/principles/deep/README.md) (temporal coupling) |
| 10 | Framework imports the SUT: DTOs, mapper, rules as the oracle | `steps/Expected.java`, `steps/UserSteps.java:37` | wrong result (tautology) | no | `P10` | [`dry`](../principles/src/main/java/com/study/principles/dry/README.md) (tautological expectation), [`dip`](../principles/src/main/java/com/study/principles/dip/README.md) |

## The problems

### 1. `BaseTest` — one class owns every resource

**Symptom.** `UsersApiTest` launches Chromium and opens a JDBC connection it never uses. When the database is
down (or just slow, or the CI agent has no Playwright), *API* tests fail — `P01` shows 3 API tests never started,
the class failed with `SQLException` and an NPE from `@AfterAll` on top (`db.close()` on a connection that was
never opened). **Why it hurts:** cost and failure modes of every resource are paid by every test; the base class
grows with each new need, and a subclass's own `@BeforeAll` with the same name silently replaces it.
**Fix.** Resources per need: a test declares parameters, the root (`GoodRoot`) creates only those, each with its
own lifetime. Test kinds are annotations, not superclasses (`framework/tests` `@ApiTests`, ADR 0007).

### 2. Static state shared by every test

**Symptom.** `RestAssured.baseURI` is a JVM-wide field. `P02`: two classes pointing at two stands run in
parallel — both green, both users created on stand two. The same shape: `Driver.page` (one page for all running
tests; `UsersPage` even captures it at construction — after the next `Driver.start()` it holds a closed page),
`BaseTest.db` (class A's `@AfterAll` closes the connection class B is using), `Driver` never closes Playwright.
`enableLoggingOfRequestAndResponseIfValidationFails()` is global too — and never fires, because nothing uses
`.then()` validation (#6). **Fix.** Objects with an owner and a lifetime: an immutable `UsersClient` with its own
spec; a browser context per test, closed by the root; nothing static except constants.

### 3. `FrameworkConfig` — a singleton map of strings

**Symptoms (`P03`).** The file has `ui.timeout.ms=10000`; `Driver` reads `ui.timeout` with a default of 30 s —
every UI wait is 3× longer than configured, and no one is told. `-Dheadless=false` set after the first
`getInstance()` is ignored (values frozen in a lazy, non-thread-safe singleton — two threads can create two).
`print()` writes `db.password` into every CI log. Every class reaches for `getInstance()`: a hidden dependency.
**Fix.** `Settings` — a value with a closed set of keys, parsed once by the root: unknown/missing/partial → one
error listing all problems; `Secret` masks itself in `toString`. Passed to who needs it.

### 4. "Random" emails from 1000 values

**Symptom.** `"autotest" + nextInt(1000)`: the birthday problem gives a 50 % chance of a duplicate by ~38 users;
`P04` sees duplicates in 200. On the stand that is a `409 email_taken` in some run, never reproducible, often
blamed on the SUT. Reruns and parallel CI jobs share the same space. **Fix.** `TestUsers`: run id + atomic counter,
readable (`ada-k3x9q-7@example.test` tells which run left the row), injected, not static.

### 5. Cleanup by pattern

**Symptom.** `@AfterEach` deletes *every* user whose email starts with `autotest` (`P05`): a parallel test's user
disappears mid-test (a flake in *another* test — the hardest kind to trace); a user created with any other email
is never deleted; each cleanup lists the whole table. **Fix.** `Cleanup`: an undo action is registered at the
moment something is created, run in reverse after the test — passed or failed — removing exactly that; every
action runs, failures are collected; delete is idempotent (404 is fine).

### 6. A client that returns `Response`

**Symptom.** `createUser` returns whatever came back; `UserSteps.createUser` stores `jsonPath().get("id")` —
`null` for a 400. `P06`: the step passes, `userShouldExist()` later fails with an NPE that does not mention 400 or
`age_too_low`. The diagnosis needs a rerun with logs. **Fix.** Deep client: `create(NewUser)` returns a `User`
or throws `ApiException` with request, status and body; `tryCreate` returns the response for negative tests —
two entry points, not one shallow one (`principles/deep`).

### 7. Page object that asserts, sleeps and prepares data

**Symptoms (`P07`).** `createUser` ends with `assertThat(notice visible)`: a negative scenario ("underage user
is rejected with `age_too_low`") cannot be written — it is an `AssertionError` inside the page. Fixed sleeps:
≥ 3 s per create whether the page needs 50 ms or 5 s (too slow and still flaky). `verifyUserInTable` checks
`page.content()` — the whole HTML, including the re-rendered form with the typed email: it **passes for a user
that was not created**. `createUserViaApi` puts the API channel into the page. **Fix.** Actions and queries,
no assertions; each action waits for its own outcome (Playwright auto-wait); a rejection is a return value
(`rejectedWith`); checks read the list (`shownEmails`), not the page source.

### 8. `DbUtils`

**Symptoms (`P08`, compose stand).** `"... WHERE email = '" + email + "'"`: `o'brien@example.com` — a valid email
for the SUT — breaks the query; `x' OR '1'='1` "finds" the first row of the table. `Statement`/`ResultSet` are
never closed (leaks over a long run). Not found = empty `Map`; columns are strings and casts in every test.
**Fix.** `UserRows`: bound parameters, try-with-resources, a typed `UserRow`, `Optional`, "more than one row" is
an error; the only class that knows the columns.

### 9. `ScenarioContext`

**Symptoms (`P09`).** Steps communicate through a static `HashMap` with string keys: scenario A checks scenario
B's user (the interleaving of a parallel run, on one thread); a step called before `createUser` dies with an NPE
that does not name the missing step; types are casts (`(Integer)` today, a `ClassCastException` when the id
becomes `Long`); `HashMap` under concurrent writes can lose entries. **Fix.** Steps return what they made; the
test holds it in a local variable. The compiler checks order and types; nothing is shared.

### 10. The framework is built from the SUT's own classes

**Symptom.** `Expected.user()` calls `UserRules.validate` — the SUT's validation — to compute the expected user,
and `UserSteps` parses responses with the SUT's `User` and `SutApp.objectMapper()`. `P10`: the expectation *is*
the code under test; a regression in normalization changes the expectation in the same build — green. A renamed
JSON field is renamed on both sides — green, while real clients break. Also: the framework can only be built
with the SUT's jar of the same version. **Fix.** Own DTOs (the framework's copy of the contract), literal
expectations in tests (`"GB"`, not `rules.normalize("gb")`), an architecture test: no `com.study.sut` import in
framework code. Starting the SUT in-process for tests is fine — that is environment, not oracle.

## Decoys (not problems)

| # | Looks like | Why it is right |
|---|---|---|
| D1 | `switch (profile)` in `core/Driver.java:34` | one place, two stable variants, an unknown profile fails fast with the list (`DecoysReviewTest`). Abstract at the third variant or the second switch (`principles/ocp`). |
| D2 | chains `given().contentType().body().when().post().then().extract()` in `api/UserApi.java` | a fluent API returning the same builder — not a Law of Demeter violation (`principles/demeter`). The problem of `UserApi` is #6, not the chain. |
| D3 | `catch (Exception e)` in `core/ScreenshotOnFailure.java:18` | evidence must not replace the failure: the screenshot's error is added as suppressed and the original rethrown (`DecoysReviewTest`; the same finding as in `framework/tests`). Its coupling to the static `Driver` is #2. |

Also fine: `UsersPage` opens the collapsed form **by its state** (`isVisible`), not by `if (mobile)` — the
approach of `framework/ui`.

## Other valid findings (not planted, still worth a comment)

`sleep` swallows `InterruptedException`; `UsersPage` and `TestData.cleanUp` create `new UserApi()` inside
(hidden dependencies); request bodies are `Map<String, Object>` (typos in keys, no defaults); screenshot file names
collide for parameterized tests; `UsersApiTest.createsUser` mixes three stories; `DbUtils` is static and takes a
`Connection` from a static field. If you found these — they count.

## Order of refactoring in a real team

1. **Stop the lies first** — green tests that check nothing: #10 (oracle), #7 (`page.content()` check), #6
   (unchecked status). Small, local changes; each one can turn a green test red — that is the point.
2. **Data isolation** — #4, #5: removes flakes in *other* tests and makes parallel runs possible at all.
3. **Lifetimes and state** — #9, #2, then #1: introduce the root and per-test objects *next to* `BaseTest`
   (strangler), migrate test classes one by one, delete `BaseTest` last. Turn on parallel execution only after this.
4. **Config and DB** — #3, #8: mechanical, low risk, once there is a root to pass `Settings` from.

Before step 1: run the suite against the stand a few times and record what is green, flaky and red — the
characterization baseline that tells a refactoring regression from an old flake.
