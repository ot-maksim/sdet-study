# ADR 0005 — UI layer: leased browsers for parallel tests, profiles as context options, screen interfaces

Status: accepted (stage 2).

## Context

Plan decision: Playwright for web and mobile web (device emulation); native mobile later via Appium behind the
same interfaces. UI tests are the slowest, so they should be able to run in parallel from the start. Playwright
Java objects are not thread-safe.

## Decision

- `BrowserPool` (run-scoped) holds `Playwright + Browser` workers; a test leases one exclusively through a
  `BlockingQueue`, launched lazily up to `ui.max-browsers`. `UiSession` (test-scoped) = lease + new
  `BrowserContext` + `Page`, closed by the test's `Cleanup` — also when the test failed.
- Profiles (`desktop`, `mobile`) are only `BrowserContext` options. Page objects do not know the profile; components
  react to what the page shows.
- Page objects are composed of components; locators are `data-testid`s; every action waits for its visible outcome.
- Screens have an interface (`UsersScreen`) and a contract test. A native client will be another implementation
  passing the contract; no stub that throws.
- Browsers are installed explicitly (Chromium only); the framework never triggers a download.

## Alternatives

| Option | Why not |
|---|---|
| One shared browser, UI tests sequential | Simple, but parallel UI later would mean redesigning lifetimes |
| `ThreadLocal` browser | Hidden lifetime, leaks, wrong unit of parallelism (principles/parallel) |
| Browser per test | Correct, slower by a launch per test |
| `if (mobile)` in page objects / `Mobile*Page` subclasses | Branching spreads; hierarchy per device (principles/inheritance, `pages`) |
| Stub `NativeUsersScreen` throwing `UnsupportedOperationException` | Violates the contract (principles/lsp) |

## Consequences

- \+ UI tests are parallel-safe now; the composition root decides the parallelism through `ui.max-browsers`.
- \+ Adding a profile is one registry entry; adding native is one implementation plus the existing contract.
- − Several browser processes in memory; the pool limit must be at least the JUnit parallelism or tests wait.
- − `UsersScreen` has one implementation today; it is kept because native is planned, not as a general rule.
