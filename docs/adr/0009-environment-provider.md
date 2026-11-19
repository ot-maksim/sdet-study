# ADR 0009 — Environment provider: a running stand or Testcontainers

Status: accepted (stage 4a).

## Context

Stage 2 connected to a stand that somebody else started (`docker compose up`), with URLs in config — on purpose:
an E2E framework usually does not own its environment (PLAN, "Why Testcontainers is not in stage 2"). A working
template also needs the opposite mode: a hermetic run in CI or on a laptop without a stand, a fresh database per
run, no fixed ports, two runs side by side. Tests must not know which mode ran.

## Decision

- One seam, `EnvironmentProvider`: `unavailable()` (why it cannot be used, found without starting anything) and
  `start()` → `SutEnvironment` (SUT URL, JDBC URL, user, `Secret` password). Every client is built from
  `SutEnvironment` in the root, never from a provider-specific key.
- Two implementations, chosen by `template.environment` (an enum, one `switch` in the root):
  - `StandEnvironment` — connects; probes `/health` and the `users` table, as stage 2 did;
  - `ContainersEnvironment` — Testcontainers: Postgres + the SUT image built from `sut/Dockerfile` and
    `sut/target/sut.jar` on a private network; random host ports; a generated database password.
- **Containers is the default**: `mvn verify` needs only Docker. `-Dspring.profiles.active=stand` runs against a
  stand. Unavailable → skipped with the reason; profile `ci` (`template.require-environment=true`) → failed.
- Started once per Spring context (lazy bean, cached context), stopped when the context closes; Ryuk removes the
  containers if the JVM dies.
- Maven builds the SUT before the template (`sut` as a `type=pom` test dependency with all exclusions: build order,
  nothing on the class path).

## Alternatives

| Option | Why not |
|---|---|
| Spring Boot `@ServiceConnection` / `@Container` fields | Feeds Spring's own `DataSource` etc.; our clients are not auto-configured, and the provider would be split over test classes |
| Testcontainers JUnit extension (`@Testcontainers` + static fields) | Lifetime per test class, not per run; every class restarts Postgres |
| Docker Compose module of Testcontainers (`ComposeContainer`) | Reuses `docker-compose.yml`, but fixed ports and one project name collide with the developer's own stand |
| SUT in-process (as the api module's tests) | Fast, but not the artifact that is deployed; the image and its env-var config stay untested |
| Image from a registry instead of a local Dockerfile | The real-life choice; here the SUT lives in the repo, so the image is built from it. One key (`postgres-image`) shows the pattern |
| Stand as the default | `mvn verify` on a clean machine would skip every template test |

## Consequences

- \+ A clean machine with Docker runs the whole template: ~6 s to start the environment, then the same 9 tests.
- \+ No shared state between runs or developers; data isolation rules still hold for the stand mode.
- \+ A third mode (a namespace per run in Kubernetes, a cloud stand) is one class and one enum value.
- − Docker on every developer machine and CI agent (or the `stand` profile).
- − The image is rebuilt from the local jar each run (layer cache keeps it to ~3 s); a stale jar = a stale SUT —
  `-am` builds it, `-pl template` alone does not.
- − Containers start on the first test that needs the environment, so that test's time includes the start.
