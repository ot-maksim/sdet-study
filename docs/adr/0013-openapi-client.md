# ADR 0013 — API client generated from the SUT's OpenAPI, wrapped by hand; every response checked against it

Status: accepted (stage 5a, `project/junit`; the same in `project/testng`). What fails on a contract violation is
superseded by [ADR 0015](0015-contract-tests-apart.md): the filter reports, separate contract tests decide.

## Context

`framework/api` (ADR 0003) writes paths, methods and DTOs by hand. That is fine for one resource, and it hides a
risk: nothing ties the client to what the SUT promises. A renamed field or a new error code is found by a failing
assertion somewhere far from the cause, or not at all, because a tolerant reader ignores the change. A working
project usually has a contract (OpenAPI) owned by the service team. The SUT had none.

Two separate questions:

1. **Where the client's shape comes from**: hand-written or generated from the contract.
2. **Who checks that the SUT keeps the contract**: nobody, a separate contract-test suite, or every test that
   talks to the API.

## Decision

- **The contract belongs to the provider**: `sut/src/main/resources/openapi.yaml`, written for the API that exists.
  The test project never derives anything from the SUT's classes (review P10: an oracle computed by the SUT's code
  is a tautology).
- **Generate the low level, write the high level by hand.** `openapi-generator-maven-plugin` (`java`,
  `library=rest-assured`, Jackson) generates operations and models into `target/generated-sources`, package
  `api.generated`. Nothing is committed; the build regenerates it.
  - `ApiTransport` (hand-written) configures the generated client once per run: base URL, timeouts, JSON, filters.
    Every call returns the project's own `ApiResponse` with the request and the response, as in ADR 0003.
  - `UserApi` (hand-written, deep): intentions, `try*` for negative tests, "absent" = only `404 user_not_found`,
    idempotent `deleteIfExists`. It maps generated models to the project's records (`User`, `NewUser`). An
    **anti-corruption layer**: a regenerated model changes `UserApi` only; ArchUnit forbids `api.generated` outside
    the api package.
- **Every response is checked against the same file** (`ContractFilter`, Atlassian
  `swagger-request-validator`): declared status, body schema (required fields, types, formats, the enum of error
  codes), required headers. A violation fails the call with the validator's report and the exchange (the request
  and the response: method, URL, status, bodies). *Superseded by [ADR 0015](0015-contract-tests-apart.md): the
  filter only reports (a broken step); `UsersContractTest` fails, one test per response of the contract.*
  **Requests are not checked**: negative tests send what the contract forbids (age 17, a missing field) on purpose.
- The build copies the file next to the classes (`contract/openapi.yaml`): the generator and the validator read
  one source, so they cannot disagree.

## Alternatives

| Option | Why not |
|---|---|
| Hand-written client only (ADR 0003) | No link to the contract; drift surfaces late, as unrelated failures |
| Tests call the generated client directly | Tests depend on generated models (mutable, regenerated, no withers); every contract change touches tests; no `ApiResponse`, so failure messages lose the request/response pair |
| Generate from the SUT's code (springdoc-style, or share its DTOs) | The test oracle becomes the SUT itself (P10) |
| A separate contract-test suite (Pact, Schemathesis) instead of a filter | Worth it with several consumers or for fuzzing; here it would test the same responses again in another run. Complementary, not instead |
| Validate requests too | Negative tests are exactly the requests the contract rejects; they would need a switch per call |
| Strict reader (fail on unknown fields) | The validator already reports what the contract does not allow; unknown *extra* fields stay tolerated, as the contract permits them |

## Consequences

- \+ A moved path needs no edit (regenerate); a renamed operation or parameter is a compile error in `UserApi`; a
  changed response is a red test with the report
  ("createdAt is required", "age_negative is not in the enum"), seen in the first test that touches it.
- \+ A new error code is visibly an API change: the SUT team adds it to the contract, or the tests say it broke.
- \+ Tests and steps did not change: they still use `UserApi`, records and `ApiResponseAssert`.
- − The generator is a build dependency with its own options and quirks (a schema named `Error` becomes a class
  `Error` in the generated package; `null` handling is configured, not default); upgrades need a look at the diff.
- − Validation costs a few milliseconds per call and reads the contract once per run.
- − The contract here is hand-written next to the code; nothing on the provider side checks that the SUT matches it
  — the consumer's run does. A provider-side check (the SUT's own tests validating against the file) is the next
  step when a second consumer appears.
