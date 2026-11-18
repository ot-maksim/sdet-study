# Page hierarchy vs components

A screen is a set of **independent** widgets: header, sidebar, search box, table. Any page has any subset,
and each widget may render differently per device. A class hierarchy fits one axis and one nesting; this
is two axes and a set, so the tree either duplicates code or gives pages methods they cannot honour.
**Components** model it directly: a page *has* the widgets it shows; the device decides which implementation
of a widget every page gets, in one place.

| Package | What to look at |
|---|---|
| `bad` | `BasePage → AuthenticatedPage → PageWithSidebar → PageWithSidebarAndSearch`; `SettingsPage` inherits `navigateTo()` because search lives only in the "with sidebar" branch; `MobileDashboardPage` and `MobileSettingsPage` copy the same burger-menu overrides; there is no `MobileReportsPage` |
| `good` | `Header` (`DesktopHeader`, `MobileHeader`), `Sidebar`, `SearchBox`; pages are `final` and expose only their components; `Pages` picks the header implementation once |
| root | `FakeBrowser` — three pages with different widget subsets, desktop/mobile rendering; `Device`; `PagesDemo` |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.inheritance.pages.PagesDemo
mvn -q -pl principles test -Dtest='PageHierarchyVsComponentsTest'
```

- **bad** — `settings.navigateTo("reports")` compiles and fails (refused bequest = LSP violation); on mobile,
  `ReportsPage.logout()` fails because nobody wrote the mobile leaf; the burger-menu override exists twice.
- **good** — one parameterized test covers *every* page with a header on *every* device; `SettingsPage` has no
  `sidebar()`, so the mistake is a compile error.

## Counting classes

| Change | `bad` | `good` |
|---|---|---|
| New page with header + search | find a parent that has search (drags the sidebar) or add `PageWithSearch` + duplicate `search()` | one class with two fields |
| New device (tablet) | a `Tablet*Page` per page | one `TabletHeader` + one line in `Pages` |
| Header selector changes | `AuthenticatedPage` + every `Mobile*Page` | one component |
| Widget in a modal / another page | impossible to inherit into a modal | reuse the component with a different root |

## Pros / cons

| Pros | Cons |
|---|---|
| Pages list what they have; no refused methods | One more hop in tests: `page.header().logout()` |
| Device/theme differences live in one component implementation | Components need a root/scope (in Playwright: a `Locator` to search within) |
| Components are reusable and testable on their own | Over-splitting: a component per button is noise |
| No combinatorial classes | Shared page-level behaviour (wait for load, URL) needs its own small place |

## When NOT to apply
- **A tiny, stable app** — three pages, one device: plain page objects with a shared `open()` are fine.
- **A thin `BasePage` with a real invariant** — "every page waits for its own ready marker on open" as a `final`
  template method with one `protected abstract readyMarker()` is legitimate inheritance (compare
  [`session`](../session/README.md)). Keep it one level and free of widgets.
- **Splitting into components everything the page touches** — a component earns its place when it repeats
  across pages or varies per device; a one-off form section can stay private to its page.
- **Exposing components when the test wants intent** — `page.header().logout()` is fine; a 3-step chain through
  components is a Demeter smell, hide it behind a page action (see [`demeter`](../../demeter/README.md)).

## Try it yourself
1. Add `MobileReportsPage` to `bad`. How many places now hold the burger-menu code? Change the burger selector
   to `#menu` and count edits in `bad` vs `good`.
2. Add a `/profile` page with header + search, no sidebar, in both packages. Which parent did you pick in `bad`?
3. Add a tablet profile where the sidebar collapses (`#drawer` then `#nav-*`). Sidebar becomes an interface in
   `good` — where does the device decision go? What happens to the `bad` tree?
4. Make `Header` take a root selector (`new DesktopHeader(browser, "#modal")`) and reuse it inside a dialog.
5. Smell check on your framework: page class names with `With`/`And`, `Mobile` prefixes duplicating desktop
   classes, methods throwing `UnsupportedOperationException` in page objects.
