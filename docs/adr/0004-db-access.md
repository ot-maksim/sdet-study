# ADR 0004 — DB access: JDBC template + table repositories, own row types

Status: accepted (stage 2).

## Context

E2E tests need to check what the SUT stored (normalized email, timestamp, no duplicate after a rejected create)
and to seed preconditions without the slow path. Plan: JDBC to Postgres in `docker compose`; no Spring, no
Testcontainers before stage 4.

## Decision

- `Database`: a stateless JDBC template (connection per call, ordered parameters, `Instant` binding, SQL in every
  error). Run-scoped.
- One repository per table (`UserRows`) owns column names and mapping; tests never write SQL.
- Own row types (`UserRow`), not the api's DTOs: `db` depends on `core` only. The domain layer relates API and DB views.
- Seeding is allowed but documented as bypassing the application.
- Tests needing a database are skipped with a reason when it is unreachable; hermetic DB tests come with
  Testcontainers in stage 4 as another way to provide the same `DbSettings`.

## Alternatives

| Option | Why not |
|---|---|
| Reuse the SUT's `JdbcUserRepository` | The test would trust the code under test to read its own writes |
| JPA / jOOQ / Spring JdbcTemplate | More machinery than a handful of statements; Spring at stage 4 |
| H2 in Postgres mode for module tests | Different engine: the rounding and `char(2)` findings would not show up |
| A connection pool | No measured need; a pool is state to configure and close |

## Consequences

- \+ Failures name the statement; schema knowledge is in one class per table.
- \+ The same tests run against any Postgres reachable through config (compose today, a container tomorrow).
- − DB checks couple tests to the schema — use them for what the API cannot show.
- − Without Docker the DB tests are skipped, so a green `mvn verify` alone does not prove them.
