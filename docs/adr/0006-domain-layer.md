# ADR 0006 — Domain layer: steps facade, preconditions behind an interface, cross-channel checks

Status: accepted (stage 2).

## Context

Scenarios need users as preconditions (often, fast), act through a specific channel (API or UI), and check
results across channels (API vs table vs page). The `dip` kata showed a test that knows SQL; the `kiss` kata a
step with boolean flags that mixed preparation, action and verification.

## Decision

- `UserPrecondition` interface with API and DB implementations; the composition root picks one
  (`domain.user-precondition`). Both pass one contract: a seeded user is indistinguishable through the API from an
  API-created one.
- `UserSteps` (test-scoped) separates preconditions (`existing`) from actions under test (`registerThrough*`,
  channel in the name). Every created user registers its cleanup immediately — also UI creations (lookup by email)
  and negative attempts (in case the SUT wrongly accepted them).
- `UserChecks` is the one place relating `User` (API), `UserRow` (DB) and `ShownUser` (page).
- Domain reuses the api DTOs (`NewUser`, `User`) as the scenario vocabulary instead of a third model.

## Alternatives

| Option | Why not |
|---|---|
| Tests call clients directly | Fine for a few tests; with many, channel choice and cleanup repeat in every test |
| One `createUser(channel)` with an enum | A flag that changes behaviour; precondition and action are different intentions |
| Own domain model (`Customer`) | A mapping layer with no second consumer yet — YAGNI |
| DB precondition only | Fast, but every test would run on states the SUT did not produce |

## Consequences

- \+ Switching precondition speed is configuration; scenarios read as business steps.
- \+ No leaks from any channel; leaks fail the test (core `Scope`).
- − The DB precondition copies SUT normalization; the contract test is mandatory, not optional.
- − Domain depends on every client module — expected for the top of the client stack, below `tests`.
