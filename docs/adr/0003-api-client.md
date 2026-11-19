# ADR 0003 — API client: transport + deep resource clients, own response type

Status: accepted (stage 2).

## Context

Tests and (later) domain steps call the users API. Stage-1 katas showed the traps: a shallow `HttpHelper` that
leaks paths and statuses into every test ([`deep`](../../principles/src/main/java/com/study/principles/deep/README.md)),
a shared mutable `RequestSpecification` under parallel runs ([`parallel`](../../principles/src/main/java/com/study/principles/parallel/README.md)),
helpers whose failures need a debugger ([`dry`](../../principles/src/main/java/com/study/principles/dry/README.md)).

## Decision

- **Transport** `ApiClient`: base URL, timeouts, JSON mapping; immutable, run-scoped; a new request per call from
  a prebuilt spec; no RestAssured static state.
- **Resource clients** (`UserApi`) express intentions and own paths, statuses and parsing. Happy-path methods
  return DTOs and throw `ApiException` with the whole exchange; `try*` methods return `ApiResponse` for negative tests.
- **Own `ApiResponse`** instead of RestAssured's `Response`: tests do not import `io.restassured`, and every
  failure message has the request and the response.
- "Absent" is the SUT's own `404 user_not_found`, not any 404.
- Request DTOs are records with withers and nullable fields; `NewUsers` gives a valid, unique starting point.
- The client is tested against the real SUT in-process (test-scoped dependency on `sut`), not against a mock.

## Alternatives

| Option | Why not |
|---|---|
| Tests use RestAssured DSL directly | Paths and statuses in every test; an API change is an edit per test |
| Return RestAssured `Response` from clients | Couples tests to the library; no request in failure messages |
| WireMock / a hand-written fake server for client tests | Can drift from the real service; here the real one starts in milliseconds |
| Generated client from OpenAPI | The SUT has no spec; a generated client is shallow (one method per endpoint) anyway — a deep layer would sit on top |

## Consequences

- \+ An API change is one edit; a transport change touches one class.
- \+ Negative tests read as the rule: `isRejectedWith(400, "age_too_low")`.
- − `framework-api` has a test dependency on `sut` — fine for a monorepo; with a separate SUT repo the same tests
  would run against a stand or a contract stub.
- − Two methods per operation; the `try*` side grows with negative cases.
