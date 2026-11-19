# sdet-study

Study repo: OOP and software architecture in practice, with test-automation examples.
The user is an experienced Java SDET; act as a mentor/architect: explain briefly, show pros/cons
and when NOT to apply, then reinforce with code. Practice over theory.

**Source of truth: [docs/PLAN.md](docs/PLAN.md)** (decisions, stages, checklist). Read it at the start
of a session; tick items there when a topic is done.

## Language
- Explanations and discussion with the user: Russian.
- Code, comments, Javadoc, READMEs, commit messages: English.

## Modules
- `basics/` — Java basics (init order, builder).
- `principles/` — katas, one package per topic: `com.study.principles.<topic>[.<subtopic>]`.
- `sut/` — system under test (users REST + HTML, Postgres); stand via `docker-compose.yml`. See `sut/README.md`.
- `framework/` — stage 2 skeleton, one Maven module per layer (`framework/<layer>`, artifact `framework-<layer>`,
  package `com.study.framework.<layer>`); clients (`api`, `db`, `ui`) depend on `core` only, `domain` on clients, `tests` on all; the composition root (bindings of
  all modules) lives in `framework/tests`. Key decisions as ADRs in `docs/adr/`.

## Kata format (principles/)
1. `README.md` in the topic package: the principle in 2–3 lines, a table of packages and what to
   look at, run commands, pros/cons, when NOT to apply, "Try it yourself" exercises.
2. `bad/` — realistic automation code with the smell; `good/` — refactored version
   (extra variants such as `composition/` are fine when they teach a contrast).
3. Tests in `principles/src/test/java` that make the difference visible (bad fails or is awkward,
   good passes). Use JUnit 5 + AssertJ; jqwik when a property says more than examples.
4. No external systems: fakes only (FakeBrowser, in-memory repos, printed "HTTP" calls).
5. A runnable `*Demo` with `main` when the behaviour is easier to see than to assert.

Run: `mvn -q -pl principles compile exec:java -Dmain.class=<fqcn>`; tests: `mvn -q verify`.

## Constraints (agreed, do not change without the user)
- Stage 2 framework is a **learning skeleton**: minimal but architecturally correct layers.
- DI by hand (composition root in a JUnit 5 extension). No Spring, Testcontainers, Allure,
  Appium or CI before stage 4.
- Clients: RestAssured (API), Playwright (UI web + mobile-web emulation), JDBC (DB, Postgres via docker compose).
- Java 17; dependency and plugin versions only in the parent `pom.xml`.

## Workflow
- One session per stage block (slicing in docs/PLAN.md). Work on `main`, no feature branches.
- One commit per topic; commit only when the user asks. `mvn -q verify` must pass before committing.
- The user also adds katas in parallel: check `git log` / `git status` before editing shared files
  (especially `pom.xml`, `docs/PLAN.md`) and never overwrite files you have not just read.
