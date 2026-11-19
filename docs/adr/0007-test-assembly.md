# ADR 0007 — Test assembly: test kinds as annotations, stand policy, parallel by default

Status: accepted (stage 2).

## Context

The modules exist (core, api, db, ui, domain); something must put them together, choose implementations and
decide how the suite behaves when the stand is missing or a test fails.

## Decision

- `framework/tests` holds the composition root `SutComposition` (ADR 0001) and the stand file `env/local.properties`.
  Implementation choices are configuration: precondition channel, browser profile.
- Test kinds are meta-annotations (`@ApiTests`, `@UiTests`, `@E2eTests` = tag + root), not base classes.
- Stand down: skip with the reason by default (local runs, `mvn verify` without Docker); `stand.required=true`
  makes it a failure (CI).
- The suite runs in parallel by default (4 threads); isolation by unique data and leased browsers; nothing counts
  rows or relies on order.
- A failed test that had a browser leaves a screenshot; a test without a browser never launches one for it.

## Alternatives

| Option | Why not |
|---|---|
| Base classes `BaseApiTest` / `BaseUiTest` | Inherited setup and state (principles/inheritance, `basetest`) |
| Fail when the stand is down, always | `mvn verify` would need Docker for every change in principles |
| Skip silently | A green run that tested nothing; the reason is always printed, CI turns it into a failure |
| Sequential suite, parallel "later" | Parallel safety is a design property; retrofitting it means redesigning lifetimes |
| Allure attachments for screenshots | Stage 4; a file in `target/` is enough for the skeleton |

## Consequences

- \+ Tests contain only the scenario; switching channel, profile or stand needs no code change.
- \+ A missing stand is visible and explained, locally and in CI.
- − Skipped-by-default means a local green run must be read: "9 skipped" is not "9 passed".
- − Parallel by default requires every new test to follow the isolation rules (unique data, find by key).
