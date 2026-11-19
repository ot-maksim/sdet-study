# framework/ui

UI client on Playwright (Chromium): browser lifecycle that allows **parallel UI tests**, desktop / mobile-web
profiles, page objects built from components, and a screen interface a native client could implement later.

| Class | What to look at |
|---|---|
| `UiSettings` | `ui.base-url`, `ui.profile`, `ui.headless`, `ui.timeout`, `ui.max-browsers` |
| `BrowserPool` | run-scoped; a test **leases** a whole `Playwright + Browser` exclusively (`BlockingQueue`), launched lazily up to `ui.max-browsers`; never downloads browsers behind a test's back |
| `UiSession` | test-scoped: lease + fresh `BrowserContext` (own cookies/storage) with profile and base URL + one `Page`; closing returns the browser |
| `BrowserProfile` / `BrowserProfiles` | `desktop` (1280×800), `mobile` (390×844, scale 3, touch, mobile UA); unknown name lists the known ones |
| `users.UsersScreen` | the screen as a person uses it; find by email, never by position or count |
| `users.UsersPage` | the web implementation, composed of `NewUserForm` and `UsersList` (no base page class) |

## Install Chromium once

Playwright needs its own browser build. Only Chromium is used (mobile web is Chromium device emulation):

```
mvn -q -pl framework/ui -am compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt -Dmdep.includeScope=compile
java -cp "$(cat framework/ui/target/classpath.txt)" com.microsoft.playwright.CLI install chromium
```

It lands in `~/Library/Caches/ms-playwright` (Playwright 1.49: Chromium, the headless shell and ffmpeg — ~450 MB).

Tests: `mvn -q -pl framework/ui -am test` — the real SUT in-process, real headless Chromium:
`UsersScreenContract` for both profiles, layout differences, and `ParallelUiTest`, which runs 8 UI tests on
4 threads and checks they overlapped, stayed within the pool and all passed.

## Parallel UI tests: how

Playwright Java is not thread-safe: a `Playwright` and everything it created must be used by one thread at a
time. Options and the choice:

| Option | Verdict |
|---|---|
| One browser per run, shared | Breaks under parallel tests (concurrent calls into one `Playwright`) |
| `ThreadLocal<Browser>` | Ties a browser to a scheduler thread; JUnit may run a test's parts on other threads, and nobody closes it (principles/parallel) |
| `Playwright + Browser` per test | Correct, but pays a browser launch (~0.5 s) per test |
| **Pool of `Playwright + Browser`, leased per test** | Correct and cheap: a browser is used by one test at a time; the queue hand-over orders memory between threads |

Data isolation is the other half: tests share the stand, so each creates its own user (`Unique`) and finds it by
email; nothing counts rows or takes "the last one".

## Findings

- **A readiness marker must be visible in every layout and with any data.** `open()` waited for `users-table`;
  on a phone with no users the table has zero height (header hidden, no rows) and is "invisible". Sequential
  runs hid it — earlier tests left rows; the parallel run starts with an empty list and caught it.
- **Playwright regexes run in the browser.** `Pattern.quote` produces Java's `\Q...\E`, which a JavaScript RegExp
  does not understand — the exact-email filter silently matched nothing. Escape characters one by one.
- **Branch on what the page shows, not on the device.** `NewUserForm` opens the form if it is not visible; there
  is no `if (mobile)`, so the code survives a moved breakpoint or a tablet profile.
- `textContent`, not `innerText`, for cells: the card layout prefixes them with CSS `::before` labels.

## Decisions

[ADR 0005 — UI layer: leased browsers for parallel tests, profiles as context options, screen interfaces](../../docs/adr/0005-ui-layer.md)

## Pros / cons

| + | − |
|---|---|
| UI tests can run in parallel safely; browsers launched only when needed | Up to `ui.max-browsers` browser processes in memory |
| One page object for desktop and mobile web | Visual differences between profiles need their own (layout) tests |
| `UsersScreen` leaves room for a native implementation without stubs | An interface with one implementation today — justified only by the planned native client (ADR) |

## When NOT to do this

- A handful of UI tests run sequentially: one `Playwright` per run is simpler.
- Layout-heavy apps where mobile is a different flow, not a different layout: separate screen implementations.

## Try it yourself

1. Set `ui.max-browsers=1` in `UiTestRoot` and run `ParallelUiTest`: what happens to the time and to `maxInUse`?
2. Remove the `exact` email filter in `UsersList.row` and create `ada-x-1@…` and `ada-x-11@…`: which test fails?
3. Add a `tablet` profile (820×1180). Which code changes? Which tests?
