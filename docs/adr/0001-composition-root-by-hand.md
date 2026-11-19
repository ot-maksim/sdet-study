# ADR 0001 — Composition root by hand, mechanics in core

Status: accepted (stage 2). Revisit at stage 4 (Spring test context).

## Context

Tests need clients (API, DB, browser pages) and steps built on them, with two lifetimes: once per run
(HTTP client, connection pool, browser process) and once per test (page, created data). The stage-1 katas
showed the failure modes: `new` inside page objects and static `DriverManager`
([`coupling`](../../principles/src/main/java/com/study/principles/coupling/README.md)), a `BaseTest` god class
([`inheritance`](../../principles/src/main/java/com/study/principles/inheritance/README.md)), shared mutable state
under parallel runs ([`parallel`](../../principles/src/main/java/com/study/principles/parallel/README.md)).
Decision 4 of the plan: DI by hand, no container until stage 4.

## Decision

- Objects receive dependencies through constructors only (DIP); none of them looks anything up.
- One **composition root** per test suite: a JUnit 5 `ParameterResolver` in the top module (`tests`), the only
  class that knows every module. It extends `core.inject.CompositionRoot` and implements one method,
  `bind(Bindings)`, where each type gets a lifetime (`RUN` / `TEST`) and a hand-written factory.
- `core` provides the mechanics, not the wiring: lazy creation per scope in the JUnit Store, closing via
  `Cleanup`/`Scope` in reverse order (also after a failure), and guards with path messages — test object in
  `@BeforeAll`, RUN object depending on TEST (captive dependency), cycles.
- Tests declare parameters. No base test class, no statics, no `ThreadLocal`.

## Alternatives

| Option | Why not now |
|---|---|
| Wiring in `core` | core would depend on api/ui/db — the dependency arrow inverted, every new module edits core |
| Plain classes `RunComponents` / `TestComponents` with fields | Most "pure", fully compile-checked, but eager: every API test builds a browser page; lazy fields re-implement the same table by hand |
| `BaseTest` with protected fields | Every test pays for every resource; setup order by inheritance (the `basetest` kata) |
| Guice / Spring | Planned for stage 4, to compare against this — adding it now hides what it does |

## Consequences

- \+ All `new`s in one place; a test's needs are visible in its signature; scopes are explicit and tested.
- \+ Swapping an implementation (in-memory repo, remote browser, Testcontainers environment) is one line in `bind`.
- − `Bindings` is a tiny container (type → factory). It stays "DI by hand" only while `Components.get` is used
  in factories and nowhere else; a step that takes `Components` is a service locator.
- − `CompositionRoot` is an abstract class: JUnit instantiates `@ExtendWith` classes reflectively, so the wiring
  must come with the class. Template method with one hook and no subclass state — the acceptable kind of inheritance.
