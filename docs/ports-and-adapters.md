# Ports and adapters (hexagonal architecture) vs layers, and when an integration test is unavoidable

A reference note. Companion to [test-doubles.md](test-doubles.md): a double is only possible where there is a
port, and whether the business rules can be checked without integration tests is decided by where the
dependencies point.

## Terms

- **Hexagonal architecture** (Ports and Adapters, Alistair Cockburn, 2005): the core (business rules) depends on
  nothing external; everything external — HTTP, database, Kafka, UI, a report tool — plugs in from outside. The
  hexagon is just a shape with many sides: there is no "top" and "bottom", only **inside and outside**. UI and
  database are equally outside. The one rule: **dependencies point inward** (DIP at the scale of the application).
  Onion and Clean Architecture are the same idea with other rings and names.
- **Port**: an interface **declared by the consumer, in its own terms, for its own need**.
  - *driving* (inbound): how the outside calls the core — `UserService.create(...)`;
  - *driven* (outbound): what the core needs from the world — `UserRepository`, `UserEvents`.
- **Adapter**: an implementation of a port on a concrete technology. Driving: REST handler, CLI, a test.
  Driven: `JdbcUserRepository`, `KafkaUserEvents`, an in-memory fake.

```
            ┌──────────── adapters (technology) ─────────────┐
  REST ──▶  │                                                │ ──▶ Postgres
  HTML ──▶  │  port ──▶   CORE (domain, rules)   ──▶ port    │ ──▶ Kafka
  test ──▶  │                                                │ ──▶ in-memory fake
            └────────────────────────────────────────────────┘
   driving (who calls the core)            driven (whom the core calls)
```

### A port is not just any interface

Syntactically it is an interface; the difference is who wrote it and in whose words.

```java
// Port: the producer of artifacts (TemplateExtension) says what it needs. No Allure here.
public interface ArtifactListener {
    void artifactCreated(Artifact artifact);
}

// Adapters, chosen in the composition root (TemplateConfiguration)
final class AllureArtifacts implements ArtifactListener { ... }   // Allure.addAttachment(...)
final class ArtifactFiles   implements ArtifactListener { ... }   // target/artifacts
```

An interface copied from a vendor's API (`startStep`, `stopStep`, `updateTestCase`, ...) is not a port but a
wrapper around the vendor: the vendor leaks through it and a second implementation (files, ReportPortal) does not
fit. Dependency direction is the point: not "the extension depends on Allure", but "the Allure adapter depends on
the extension's interface".

## In this repo

| Where | Core / consumer | Port | Adapters | Chosen in |
|---|---|---|---|---|
| SUT | `users.UserService`, `UserRules` | `UserRepository` (driven) | `InMemoryUserRepository`, `db.JdbcUserRepository` | `SutMain` |
| SUT | `users.UserService` | `UserEvents` (driven) | `UserEvents.none()`, `events.KafkaUserEvents` | `SutMain` |
| SUT | `users.UserService` | its public methods (driving) | `web/` (REST and HTML) | `SutApp` |
| framework | `UserSteps` | `UserPrecondition` | `ApiUserPrecondition`, `DbUserPrecondition` | composition root / `TemplateConfiguration` |
| framework | tests, `UserSteps` | `UsersScreen` | `UsersPage` (Playwright), native (Appium, stage 4d) | `TemplateConfiguration` |
| template | `TemplateConfiguration` | `EnvironmentProvider` | `StandEnvironment`, `ContainersEnvironment` | `template.environment` |
| template | `TemplateExtension` | `report.ArtifactListener` | `AllureArtifacts`, `ArtifactFiles` | `TemplateConfiguration` |

The SUT is a small hexagon: `users/` imports nothing from `db/`, `events/` or `web/`, and its rules are tested
without Docker: `UserRulesTest` needs no double at all (pure functions), `UserServiceEventsTest` runs on
`InMemoryUserRepository` and a recording `UserEvents`.

## Layers vs hexagon

**MVC is not layered architecture.** MVC is a presentation pattern (how to split a UI into model, view and
controller); it says nothing about where the business rules live or how the database is reached. Layered
architecture is `controller → service → repository → DB`. Spring MVC applications usually combine both, hence the
confusion.

**Layers do not forbid unit tests of business rules.** If the repository is an interface (Spring Data), mock it:

```java
UserRepository repo = mock(UserRepository.class);
UserEvents events = mock(UserEvents.class);
when(repo.insert(any(), any())).thenReturn(storedAda);

new UserService(repo, clock, events).create(newAda);

verify(events).created(storedAda);   // the rule "announce what was stored", no database
```

A careful layered service with repository interfaces is about as testable as a hexagon. The difference is not
"possible / impossible":

| | Layers (typical Spring) | Hexagon |
|---|---|---|
| Who owns the interface | the data layer; shaped by persistence (`JpaRepository<User, Long>`, `Pageable`, `Specification`, JPA entities) | the core; shaped by its need (`findByEmail`, `insert`) |
| Double in a test | mostly mocks: ~30 methods of `JpaRepository`, nobody writes a fake | a fake on a `HashMap`: the port is narrow |
| Fragility | the test pins *which* repository method was called | the test checks the fake's state |

## Docker, H2 and the real criterion

"A test needs Docker" is **not** the violation. The question is **at which level the dependency is replaced**.

```java
// A: the core speaks JDBC/SQL itself
class UserService {
    private final DataSource db;                          // points outward: "a relational database"
    User create(NewUser u) { /* insert into users ... */ }
}

// B: the core declares what it needs
interface UserRepository { Optional<User> findByEmail(String email); User insert(ValidUser u, Instant at); }
class UserService {
    private final UserRepository users;
    User create(NewUser u) { /* rules, then users.insert(...) */ }
}
```

| Replace the database with | A (no port) | B (port) |
|---|---|---|
| Postgres in Docker | yes | yes, through `JdbcUserRepository` |
| H2 / embedded Postgres in memory | yes | yes, through `JdbcUserRepository` |
| a `HashMap`, no database at all | **no**: the core sends SQL, something must run it | **yes**: `InMemoryUserRepository` |

Postgres → H2 is a replacement **below the adapter**: the engine changes, the core still speaks SQL, the dependency
still points outward. In a hexagon the replacement happens **at the port**: there is no SQL in the test at all.

**The criterion:** can the business rules be checked without starting **any** external technology — neither the
real one nor an in-memory version of it? If some database (H2, embedded, a container) is needed to check a rule
that has nothing to do with storage, the core knows about the database.

H2 instead of Postgres for the **adapter's** tests is a trap of its own: dialect, types (`jsonb`, `timestamptz`),
`ON CONFLICT` differ, and the test checks SQL that will not run in production. The template uses a real
`postgres:16-alpine` (ADR 0009); the SUT's `JdbcUserRepositoryTest` runs the same `UserRepositoryContract` as the fake, against the compose Postgres.

## When an integration test is unavoidable, and when it is not

| What is checked | Unit test with a double is enough | Integration test (real technology, Testcontainers) is unavoidable |
|---|---|---|
| A rule written in Java in the core/service ("email is unique", "age ≥ 18") | yes — fake or stub of the port | — |
| The adapter itself: SQL, mapping, types, constraints | — | yes: that is the adapter's job; a mock returns what it was told |
| A rule written in `@Query` / JPQL / SQL ("active users of country X over 18") | — | yes: the rule lives in the query |
| JPA entity as the domain model: lazy loading, dirty checking, cascades | — | yes: behaviour exists only in a persistence context |
| `@Transactional`: rollback, propagation, visibility between transactions | — | yes: needs a live Spring context and a database |
| A service that uses `JdbcTemplate` / `EntityManager` directly, no interface | — | yes: nothing to replace (variant A above) |
| The contract between the fake and the real adapter | the same contract test runs on both: unit-fast for the fake, integration for the real one | |
| The deployed artifact: image, env-var config, wiring (`SutMain`) | — | yes: E2E against the image (template, containers mode) |

So: rules in code → unit tests with doubles, milliseconds. Rules in SQL, JPA or annotations → only integration
tests with a real database. Both are legitimate; the second is slower and more expensive, and should be a choice,
not the only option left.

## Questions to ask in a review of a service's testability

1. Can the service be created with `new`, without a Spring context?
2. Where do the rules live — in the Java code of the service/domain, or in queries and annotations?
3. Who owns the interfaces the core depends on — the core, or the persistence/vendor layer?
4. Is there one contract test for every fake and its real adapter?

If the developers' service has ports, ask for test adapters (an in-memory queue, a fixed clock) instead of
starting the whole stand.

## Pros and cons

| + | − |
|---|---|
| The core is tested with fakes in milliseconds, without Docker | More interfaces and mapping between core models and adapter models (DTO ↔ domain) |
| A technology is replaced in one class and one line of the root (Playwright → Appium, Allure → ReportPortal) | Ports "just in case": one implementation, no fake, no tests — ceremony |
| The test framework itself benefits: test = core of the scenario, API/browser/DB/Kafka/report = adapters | Harder for a newcomer to find "where the database is actually written": through an interface and the root |

## When NOT to apply

- CRUD without rules: the core has nothing to protect, the ports retell the table.
- Scripts, prototypes, small utilities.
- The technology **is** the subject: `EventClient` is a Kafka client; hiding Kafka behind a port inside it makes
  no sense. A port belongs to whoever *uses* the client.
- One technology, no plan to replace it, and no wish to test the consumer apart from it: a port with a single
  implementation is ceremony. `ArtifactListener` is justified because it has two adapters from the start and the
  extension is tested without Allure.

## See also

[test-doubles.md](test-doubles.md) · [`principles/dip`](../principles/src/main/java/com/study/principles/dip/README.md) ·
[`principles/lsp`](../principles/src/main/java/com/study/principles/lsp/README.md) (fakes obey the contract) ·
ADR [0009](adr/0009-environment-provider.md), [0010](adr/0010-events-kafka.md), [0011](adr/0011-report-retry-ci.md) ·
[`sut/README.md`](../sut/README.md)
