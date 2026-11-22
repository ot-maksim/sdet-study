# ADR 0012 — Module granularity: layers are packages; Maven modules follow consumers and test kinds

Status: accepted (stage 5). Applies to a working test project; `framework/` keeps one module per layer on purpose.

## Context

`framework/` splits every layer into its own Maven module (`core`, `api`, `db`, `ui`, `domain`, `tests`). In a
learning skeleton that is the point: a forbidden dependency is a compile error, and `template/` proves the layers
can be reassembled without a single edit (ADR 0008). The question is whether a team starting a real test
automation project with the same stack and channels (UI, DB, events, API) should copy that split.

What the split costs: a pom per layer, a reactor build, "which module does this class belong to", releases of
several artifacts for one change, slower IDE navigation. What it buys: compile-time enforcement of dependency
direction and the option to consume one layer without the others. The first is also available inside one module
through ArchUnit (`framework/tests` and `template` `ArchitectureTest` already check more than modules can: what a
test may touch, where a tool may appear). The second matters only when there is more than one consumer.

## Decision

- **The layering is not negotiable; the module boundary is.** Dependency direction (tests → domain → channels →
  core), one composition root, explicit lifetimes and ports hold in any project size.
- **Default: one module, layers as packages**, boundaries checked by ArchUnit rules that run in `mvn verify`
  without a stand.
- **Split into Maven modules by who consumes what, not by layer:**

| Situation | Shape |
|---|---|
| One product, one automation team, one test repo | One module; layers are packages + ArchUnit |
| Test kinds (usually by channel: API, UI, E2E) that need different heavy dependencies or their own CI job | Modules per test kind (`api-tests`, `ui-tests`, `e2e-tests`) over one shared framework module, so API tests do not pull Playwright. A selection with the same dependencies (smoke vs regression, contract vs functional) is a tag, not a module |
| Several teams or services reuse the clients | The framework is a separate, versioned library; inside it modules per channel (`-api`, `-ui`, `-db`, `-events`) so a consumer takes only what it uses |
| Microservices with component tests next to the code | Component tests live in the service repo; the shared library gives infrastructure only (config, report, transport); E2E in its own repo |

- **Share what changes rarely, keep what changes often with its owner.** Transport, configuration, reporting and
  lifecycle can be shared; domain steps and preconditions stay with the team that owns the domain, or one shared
  `UserSteps` becomes a merge-conflict and release bottleneck for five teams.
- A new module is introduced when its consumer appears (the second team, or a second test kind that needs its own
  dependencies or CI job), not in advance (YAGNI; `principles/kiss`).

## Alternatives

| Option | Why not by default |
|---|---|
| One module per layer from day one (as `framework/`) | Pays the multi-module cost for an enforcement ArchUnit gives in one module; no second consumer to justify it |
| One module, no layer rules | Layers erode silently: the first `RestAssured` call in a test body compiles and nobody notices |
| Modules per layer *and* per test kind | Matrix of poms; justified only for a library with several consuming teams |

## Consequences

- \+ A new project starts with one pom and one place to look; the structure is still visible (packages) and enforced
  (ArchUnit).
- \+ The split, when it comes, follows a real seam (a consumer, a CI job), so it is cheap to explain and to keep.
- − Enforcement moves from the compiler to a test: a violation is red in `verify`, not in the IDE while typing.
- − Extracting a module later is a refactoring (moving packages, a pom, a release); layered packages make it
  mechanical, but it is still work.
- In this repo: `framework/` stays multi-module (learning: compile-time boundaries, reuse by `template/`); the
  stage 5 working projects are single-module with ArchUnit, as this ADR recommends.
