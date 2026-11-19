# sut — the system under test

A small users service the stage-2 framework tests through three channels: REST API, HTML page, database.
It is deliberately plain (no DI container, no ORM, no migrations tool): the study object is the framework,
the SUT only has to behave like a real service — validation, conflicts, normalization, timestamps,
a responsive page.

| Package | What is there |
|---|---|
| `users` | `NewUser` (request, nullable fields) → `UserRules` (validation + normalization) → `ValidUser` → `UserRepository` → `User`; `UserService` — the use cases; `InMemoryUserRepository` |
| `db` | `JdbcUserRepository` (plain JDBC, email uniqueness from the DB, SQLState `23505` → `email_taken`), `Schema` applies `schema.sql` on start |
| `web` | `UsersApi` (`/api/users`), `UsersPage` (`/users`, server-rendered HTML), `ApiError` |
| `events` | `KafkaUserEvents` — the Kafka adapter of the `users.UserEvents` port (stage 4b); off unless `SUT_KAFKA_BOOTSTRAP` is set |
| root | `SutApp` — builds Javalin around a `UserService`; `SutMain` — config from env, picks the storage and the events adapter |

## Interface (what tests may rely on)

REST, JSON:

| Request | Response |
|---|---|
| `POST /api/users` `{"name","email","age","country"}` | `201` + `Location: /api/users/{id}` + user; `400 {"error": code, "message"}`; `409 email_taken` |
| `GET /api/users[?email=..]` | `200` list ordered by id (0 or 1 element with `email`) |
| `GET /api/users/{id}` | `200` user; `404 user_not_found`; `400 id_invalid` |
| `DELETE /api/users/{id}` | `204`; `404 user_not_found` |
| `GET /health` | `200 {"status":"UP"}` |

User: `{"id":1,"name":"Ada","email":"ada@example.com","age":36,"country":"GB","createdAt":"2026-11-19T08:16:27.492621Z"}`.

Rules (`UserRules`): name trimmed, 1..100 chars (`name_blank`, `name_too_long`); email trimmed and lower-cased,
unique ignoring case (`email_invalid`, `email_taken`); age 18..150 (`age_missing`, `age_too_low`, `age_too_high`);
country ISO 3166 alpha-2, upper-cased (`country_invalid`); unparsable body — `malformed_json`. The first failing
field wins, in that order. `createdAt` is truncated to microseconds — what Postgres stores — so the create
response equals a later read (see [`principles/time`](../principles/src/main/java/com/study/principles/time/README.md)).

HTML, `GET /users`: hooks are `data-testid` attributes — `new-user-section` / `new-user-toggle` / `new-user-form`,
`name-input`, `email-input`, `age-input`, `country-input`, `create-user`, `form-error` (with `data-error-code`),
`notice`, `no-users`, `users-table`, `user-row` (with `data-user-id`), `user-name`, `user-email`, `user-age`,
`user-country`, `delete-user`. Submit is Post/Redirect/Get: `303 /users?created={id}`; a rejected form is
re-rendered with `400`/`409`, the error and the typed values.

**Desktop vs phone** (below 600px, decided once on page load): the table becomes cards, and the create form is
collapsed behind the "New user" toggle — the same test ids, one more step to reach the form. That is the
difference the framework's web / mobile-web profiles have to absorb.

Database: table `users (id bigserial, name, email unique, age check >= 18, country char(2), created_at timestamptz)`.

Events (only with `SUT_KAFKA_BOOTSTRAP`): after a user is stored, `user.created` goes to topic `users`
(3 partitions, created by the SUT on start) — key = user id, value = the user JSON as above, header
`event-type: user.created`. Fire-and-forget: the `201` does not wait for the broker, a failed send is only logged
(no outbox: the user exists, the event is lost). A user inserted straight into the database (the framework's
DB precondition) is not announced — the SUT never saw it.

## Run

The stand (Postgres + SUT in Docker), as the framework will see it:

```
mvn -q -pl sut -am package -DskipTests
docker compose up -d --build --wait
open http://localhost:7070/users
docker compose down -v
```

Without Docker, in memory: `SUT_STORAGE=memory mvn -q -pl sut compile exec:java`.
Environment: `SUT_PORT` (7070), `SUT_STORAGE` (`postgres` | `memory`), `SUT_DB_URL`
(`jdbc:postgresql://localhost:5432/sut`), `SUT_DB_USER` / `SUT_DB_PASSWORD` (`sut`), `SUT_KAFKA_BOOTSTRAP`
(unset: no events).

With a broker (compose profile `events`, Kafka for tests at `localhost:9094`):

```
SUT_KAFKA_BOOTSTRAP=kafka:19092 docker compose --profile events up -d --build --wait
docker compose --profile events down -v
```

Tests: `mvn -q -pl sut test` — rules, the REST API and the page against an in-memory SUT on a free port with
a fixed clock. `UserRepositoryContract` runs for both repositories; the JDBC one only with a database:
`SUT_TEST_DB_URL=jdbc:postgresql://localhost:5432/sut mvn -q -pl sut test` (unique emails per test, it does
not clean the table — the rows stay visible on the page).

## Decisions and what was left out

- **One service, three channels.** The same `UserService` behind REST and HTML, so a user created through the
  API shows up on the page and in the table — the framework's E2E "API → DB → UI" has something to check.
- **Storage is swappable** (`UserRepository`, contract test for both): the SUT runs without Docker, and its own
  tests stay fast. The in-memory repository keeps email uniqueness and ordering — a lenient fake would hide
  bugs ([`principles/lsp`](../principles/src/main/java/com/study/principles/lsp/README.md)).
- **No auth, no pagination, no update.** Each would add framework code that teaches nothing new at stage 2.
  Add when a stage needs it (auth → session/token handling in `framework/api`).
- **`schema.sql` on start instead of Flyway**; **a pool-less `PGSimpleDataSource`**; **HTML by string
  formatting** with explicit escaping — enough for a test target, wrong for production.
