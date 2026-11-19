# framework/db

Direct access to the SUT's database from the test side: **verify** what the application stored and **seed**
preconditions faster than through the API. Plain JDBC behind a tiny template; one repository per table.
Depends on `core` only — not on `api`.

| Class | What to look at |
|---|---|
| `DbSettings` | `db.url`, `db.user`, `db.password` (no default: env file or `DB_PASSWORD`; a `Secret` until the driver needs it) |
| `Database` | connection per call, parameters in order (`Instant` → `timestamptz`), `SQLException` → `DbException` with SQL, params and SQL state; `queryOne` refuses to "take the first of many"; an unreachable stand → one message |
| `users.UserRows` | the only place with `users` column names: `findById`, `findByEmail`, `insert(NewUserRow)`, `delete` (idempotent) |
| `users.UserRow` | a row as stored — deliberately not the api's `User` (different reasons to change; db must not depend on api) |

Tests: `mvn -q -pl framework/db -am test`. `UserRowsTest` needs the stand's Postgres with the SUT schema
(`docker compose up -d --wait`); without it the class is **skipped with the reason** (`StandDatabase`), so
`mvn verify` passes without Docker. Every inserted row is deleted through a `Scope`.

## Findings worth remembering

- **Postgres rounds to microseconds, it does not truncate**: `.123456789` is stored as `.123457`. The SUT
  truncates *before* insert, so its rows agree with its responses; a test that compares its own nanosecond
  `Instant` with the column misses by 1 µs (`postgresRoundsToMicrosecondsNotTruncates`).
- **Seeding bypasses the application**: `insert` accepts `ADA-...@example.test` and country `XX` — rows the SUT
  would never create. Only schema constraints still apply (`23505` unique, `23514` check). Seed only states the
  application could have produced, or verify through the API afterwards.
- `char(2)` comes back padded — mapping strips it; the column type leaks unless the repository owns mapping.

## Decisions

[ADR 0004 — DB access: JDBC template + table repositories, own row types](../../docs/adr/0004-db-access.md)

## Pros / cons

| + | − |
|---|---|
| Checks the stored state, not only what the API says (normalization, timestamps, no duplicates) | Couples tests to the schema: a column rename breaks them although behaviour did not change — keep DB checks for what the API cannot show |
| Seeding is fast and can create states the API makes slow (many rows, old timestamps) | Seeding can create impossible states; it skips side effects (events, caches) of the real create |
| No ORM, no pool: every statement is visible | Connection per call — fine for a test suite, not for load |

## When NOT to do this

- The API can show the state: prefer it — it is the contract, the table is an implementation detail.
- A database you do not own (another team's service): read through their API, not their tables.
- Black-box tests of a deployed system where only the API is reachable.

## Try it yourself

1. Compare `createdAt` of a user created through `UserApi` with its row in `UserRows`: equal? Why, given the rounding above?
2. Seed a user with `email = "ADA@X.TEST"`, then call `UserApi.findByEmail("ada@x.test")`. What does the SUT answer, and who is wrong?
3. Add `UserRows.countCreatedSince(Instant)` and use it to prove a negative test did not create a row.
