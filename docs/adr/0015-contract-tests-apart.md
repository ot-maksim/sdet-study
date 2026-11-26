# ADR 0015 — Contract tests apart from functional tests: the filter reports, the contract tests decide

Status: accepted (stage 5, `project/junit` and `project/testng`). Supersedes the "every response is checked" part of
[ADR 0013](0013-openapi-client.md): what fails on a contract violation. The generated client and the contract file
stay as 0013 decided.

## Context

ADR 0013 made `ContractFilter` fail every call whose response breaks `openapi.yaml`. That couples two verdicts in one
test:

- **A minor drift blocks the functional signal.** A `createdAt` in another format, a new error code, a missing
  header: every test that touches the response goes red, also the ones whose feature works. One cause looks like a
  mass failure and hides what the run could have said about the features.
- **One reason to fail per test is lost.** A red `UsersApiTest` no longer says "registration is broken"; it may say
  "some field of some response drifted".

Serious drifts (a required field gone, a path renamed) break the functional tests anyway: deserialization in
`UserApi` fails, or the call is a 404. Separating the verdicts pays off for the minor ones, and those are the common
case.

A single "contract test" is not enough: a schema is checked on a response, and the contract describes many
(operation × status × body). `409` needs an existing user, `400 id_invalid` a path the client would never build. Some
of those responses no functional test produces at all (malformed JSON, `id_invalid`, an empty search): they are
answered by the SUT's parsing code, where a drift of the body is most likely and was seen by nobody.

## Decision

- **`Contract`** (api) is the OpenAPI file as a check of a response: `violations(ApiResponse)` lists what breaks it
  and decides nothing. One bean per run.
- **Contract tests are a kind of their own** (`@ContractTests`, `Groups.CONTRACT`; tag/group `contract`):
  `UsersContractTest`, one test per response the contract describes, 14 in all. Each asserts the status first (it picks
  the response of the operation the body is checked against, and shows the test reached that branch), then
  `conformsTo(contract)`. Nothing else: which code, which values is the functional tests' business.
  - one representative per shape: every validation code has the same `Error` body, so `age 17` stands for all;
  - separate tests for the responses the SUT answers on another path: malformed JSON, an id that is not a number
    (on `GET` and `DELETE`), an empty search result.
  - the matrix and why each row is there: the class's Javadoc and [the table below](#the-matrix).
- **`ContractFilter` reports, never fails.** It still checks every response of the run and hands each violation to
  an observer; the root passes `ContractReport`, a **broken Allure step** in the test that met it. The test keeps the
  verdict of its own assertions; the drift is visible next to it.
- **Contract coverage is a report of the whole run.** `ContractFilter` also counts every call in `ContractCoverage`
  by the operation it goes to (`GET /api/users/abc` → `GET /api/users/{id}`) and its status; when the run ends (the
  Spring context closes) `ContractCoverageFile` writes `target/contract-coverage.md`: each declared response with its
  number of calls, `never seen` for the rest, and the responses the SUT gave that the contract does not declare. A
  report, not a verdict: the end of a run is too late to fail a test, and a partial run (`-Dgroups=ui`) always looks
  incomplete. Same in both twins: the hook is the context's close, not the runner's.
- `UserApi` got `try*` for every operation (`tryGet(String id)`, `tryCreate(String rawJson)`, …) and `SutHealth` a
  `check()`: the contract tests need the response whatever it is, also for requests the client would never build.

### The matrix

| Operation | Status | How | What only this row catches |
|---|---|---|---|
| `GET /health` | 200 | call | `status` in its enum |
| `POST /api/users` | 201 | valid user, `country: "de"` | required `Location`, `createdAt` date-time, country pattern after normalization |
| `POST /api/users` | 400 | `age 17` | `Error` of the validation branch |
| `POST /api/users` | 400 | `{"name":` | `Error` of the parsing branch |
| `POST /api/users` | 409 | email of an existing user | `Error` of the conflict branch |
| `GET /api/users` | 200 | two existing users | array of `User` |
| `GET /api/users?email=` | 200 | existing email | an array of one, not an object |
| `GET /api/users?email=` | 200 | unknown email | `[]`, not 404 or `null` |
| `GET /api/users/{id}` | 200 | existing id | `User` |
| `GET /api/users/{id}` | 400 | `abc` | `Error` of the path-parsing branch |
| `GET /api/users/{id}` | 404 | deleted id | `Error` of NotFound |
| `DELETE /api/users/{id}` | 204 | existing id | no body |
| `DELETE /api/users/{id}` | 400 | `abc` | as on `GET` |
| `DELETE /api/users/{id}` | 404 | deleted id | `Error` of NotFound |

Out of the contract and not in the suite: `405` on `PUT`, `415` on a wrong content type. What the SUT answers there
is a question for the provider (a gap in the contract), not a test of ours.

## Alternatives

| Option | Why not |
|---|---|
| Keep failing every call (ADR 0013) | The problem above: a minor drift blocks the functional signal |
| One contract test | Checks one response; the others (errors, parsing branches) stay unseen |
| Collect violations from all traffic, one verdict test at the end of the run | No duplicated calls (the coverage part is taken: a report, not a verdict); but the verdict must run last — fragile under JUnit's parallel classes (a `ClassOrderer` orders within one thread), runner-specific in both twins — and a local run of one test has no verdict at all. Worth it with a large API where enumerating responses does not scale |
| No check in functional tests at all | Loses the hint "this test met a drift" that costs nothing at run time |
| Lower some rules to WARN (`LevelResolver`) | Decides what counts as minor, not who blocks whom; complementary |
| Pact / Schemathesis | Consumer-driven contracts pay off with several consumers; fuzzing explores what this matrix enumerates. Both complementary, a heavier tool for one consumer |

## Consequences

- \+ A drift is one red contract test with the report and the exchange; the functional tests say what they say about
  the features. `-Dgroups=contract` runs the contract alone, in seconds.
- \+ Responses no functional test produced are now checked; the contract has an explicit, reviewable list of what is
  covered.
- \+ The status-first rule makes each contract test prove it reached its branch: a SUT that answers `200` where `404`
  was meant is a red contract test, not a schema check of the wrong response.
- − A new operation or status in `openapi.yaml` needs a new row by hand. The coverage report shows it as
  `never seen` until someone adds it; it does not fail the build (see the decision).
- − Some calls are made twice in a run (a created user in a functional test and in the contract test): a few
  milliseconds each.
- − A violation in a functional test is a broken step, not a red test: someone has to look at the report. The
  contract tests are the gate; the step is a pointer to where the drift showed up.
