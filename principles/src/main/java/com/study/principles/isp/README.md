# ISP — Interface Segregation Principle

**No client should depend on methods it does not use.** Split wide interfaces into **roles** — cohesive sets
of methods that *clients* use together. The cut is made from the clients' side (what does `LoginPage` call?),
not from the implementation's (what can Playwright do?).

ISP vs LSP: LSP asks whether an implementation keeps the promises of its type; ISP asks whether the type
promises too much. A fat interface usually *forces* LSP violations — implementations stub what they cannot do.

| Package | What to look at |
|---|---|
| `bad` | `Driver` with 13 methods (web navigation, interaction, cookies, scripts, gestures, app lifecycle); `WebDriverImpl` stubs 4, `NativeDriverImpl` stubs 5 with `UnsupportedOperationException`; `LoginPage(Driver)` uses `open(url)` and so fails on native; `CookieLogin` accepts a native driver; `ScreenshotOnFailure` uses one method |
| `good` | roles `Navigation`, `Interaction`, `Screenshots` (functional), `Cookies`, `Gestures`, `AppLifecycle`; aggregates `Browser` / `MobileApp` for implementers; `PlaywrightBrowser`, `AppiumDevice` — no stubs; clients: `LoginPage(Interaction)`, `ScreenshotOnFailure(Screenshots)`, `CookieLogin` (intersection type), `SwipeCarousel` (two role parameters) |
| `overapplied` | `Clicker`, `Typer`, `TextReader` — an interface per method; `LoginPage` takes three of them |
| root | `FakeScreen` — the app as both platforms see it (same element ids); `IspDemo` |
| test sources | `FakeDriverForScreenshots` — the 13-method fake a 1-method client needs |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.isp.IspDemo
mvn -q -pl principles test -Dtest='IspTest'
```

`IspTest` calls every interface method by reflection and lists the ones that throw
`UnsupportedOperationException`:
- **bad**: web refuses `tap, swipe, installApp, launchApp`; native refuses `open, currentUrl, executeScript,
  addCookie, clearCookies`. The "shared" `LoginPage` fails on native; `CookieLogin` takes a native driver and
  fails; testing `ScreenshotOnFailure` takes a 13-method fake.
- **good**: nothing refused; one `LoginPage` passes on both platforms (parameterized); the screenshot client is
  tested with a lambda; `CookieLogin` with a device and `browser.swipe(..)` do not compile.
- **overapplied**: login works through three method references — and the role has no name.

## What a change costs

| Change | fat `Driver` | roles |
|---|---|---|
| Add `pushFile(path)` (native) | `Driver`, both impls (web stubs it), every fake and mock | `AppLifecycle` + `AppiumDevice` |
| New platform (desktop app via WinAppDriver) | implement 13, stub the web/mobile ones | implement `Interaction` + `Screenshots` (+ what it really has) |
| Unit-test a client | fake with 13 methods / a mock | fake of its role — often a lambda |
| A page used on a platform that lacks a method | run-time `UnsupportedOperationException` | compile error |

## Combining roles in a client

| Way | Example | When |
|---|---|---|
| One role | `LoginPage(Interaction)` | default — most clients need one |
| Intersection type | `<B extends Cookies & Navigation> loginAs(B b, ..)` | two roles that must be the *same* object; generic methods only |
| Several role parameters | `SwipeCarousel(Gestures, Interaction)` | roles may be different objects; caller passes `device, device` |
| Aggregate interface | `Browser extends Navigation, Interaction, Screenshots, Cookies` | for implementations and the composition root; a client taking it is the fat interface again |

## Pros / cons

| Pros | Cons |
|---|---|
| Signatures say what a client really uses | More interfaces to name and find |
| No stubs, no run-time "not supported" | Clients needing several roles get generics or extra parameters |
| Fakes shrink to the role; one-method roles are lambdas | Wrong cut (per implementation, per method) is worse than one fat interface |
| A change to one role recompiles/affects only its clients | Role boundaries need revisiting as clients change |

## When NOT to apply
- **Third-party APIs** — Playwright's `Page` or RestAssured's `RequestSpecification` are wide on purpose.
  Do not wrap them just to split them; apply ISP at *your* boundary (the roles your pages and steps depend on).
- **One implementation, one client** — the interface is as wide as the only client needs; splitting adds names
  for nothing.
- **Interface per method** (`overapplied`) — roles are about methods clients use *together*. Three
  one-method interfaces that every client takes together are one role cut into pieces.
- **Splitting by implementation** — `WebDriverMethods` / `MobileDriverMethods` mirrors the implementations, not
  the clients; a page that runs on both still needs both.
- **Narrow ≠ shallow** — a role with one deep method (`UserApi.create(NewUser)`, see [`deep`](../deep/README.md))
  is good; a role of five pass-through setters is not made good by being small.

Related: stamp coupling (a method taking a whole `TestContext` for two fields) in
[`coupling`](../coupling/README.md) is ISP for parameters; stubs that throw are the LSP violations of
[`lsp`](../lsp/README.md).

## Try it yourself
1. Add `pushFile(String path)` for native. Count the files you touch in `bad` (do not forget the test fake)
   and in `good`.
2. Mobile web via device emulation supports `tap` (touch emulation). Make `PlaywrightBrowser` implement
   `Gestures` — which clients now accept it, and did any `good` code change?
3. Add a `DesktopApp` (no URL, no cookies, no gestures). Which roles does it implement? Which `good` clients
   work with it unchanged?
4. Replace the intersection type in `CookieLogin` with the `Browser` aggregate. What can the method now call
   that it should not?
5. Merge `Clicker`, `Typer`, `TextReader` back into one interface. What did `overapplied.LoginPage`'s callers gain?
6. Smell check on your framework: interfaces with 10+ methods, `UnsupportedOperationException` in drivers,
   mocks with `RETURNS_DEFAULTS` for interfaces of which a test uses one method.
