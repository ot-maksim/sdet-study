# Smell cheat sheet: how to recognise it, what it breaks, where the kata is

A summary of stage 1. Start from what you **see** in a test framework (a symptom, a grep hit, a question in
review), not from the principle's name. Each row names the principle behind the symptom, the usual fix, and the
kata where `bad` and `good` sit side by side, with tests that show the difference.


## 1. In the test body

| Smell | How to spot it | Principle | Instead | Kata |
|---|---|---|---|---|
| One-line tests with positional meaning | `run(user, 400, "age_too_low", false)`; you open the helper to read the test | DAMP | builder with defaults + the one difference in the test, literal expectation | [`dry`](src/main/java/com/study/principles/dry/README.md) |
| Expectation computed from the app | `MIN_AGE - 1`, `price * TAX_RATE` copied from prod, imports of production constants | DRY ≠ tautology | literals, or an independent oracle checked by examples | [`dry`](src/main/java/com/study/principles/dry/README.md), [`srp`](src/main/java/com/study/principles/srp/README.md) |
| Failure needs a debugger | `assertTrue(a.equals(b))`, `verifyAll(response)` | DAMP | AssertJ field checks / custom assertion naming field, expected, actual | [`dry`](src/main/java/com/study/principles/dry/README.md) |
| Same request code in every test | path, headers, JSON parsing repeated; an endpoint rename = an edit per test | DRY (mechanics) | client / page object owns the mechanics | [`dry`](src/main/java/com/study/principles/dry/README.md), [`deep`](src/main/java/com/study/principles/deep/README.md) |
| Chains into page internals | `page.getHeader().getMenu().getItem("x").click()` | Law of Demeter | intention method on the page (`app.logout()`) | [`demeter`](src/main/java/com/study/principles/demeter/README.md) |
| SQL / REST / Playwright in the test | `import java.sql`, `io.restassured`, `com.microsoft.playwright` in test classes | DIP | domain precondition (`UserPrecondition`), channel decided below | [`dip`](src/main/java/com/study/principles/dip/README.md) |
| `now()` in a test or the SUT seam | `now()`, `new Date()`, `currentTimeMillis`; flaky at midnight / DST / year end | hidden input | injected `Clock`, right type, assert a window | [`time`](src/main/java/com/study/principles/time/README.md) |
| `Thread.sleep` as synchronisation | `sleep` before an assertion, `while (true)` + `catch (Exception)` | wait for a condition | event subscription before the action, poll with a deadline | [`events`](src/main/java/com/study/principles/concurrency/events/README.md), [`eventual`](src/main/java/com/study/principles/concurrency/eventual/README.md) |
| Assertion where nobody hears it | `assert` inside `thenAccept`, a listener, a webhook handler | failures belong to the test thread | record, then assert in the test with a deadline | [`futures`](src/main/java/com/study/principles/concurrency/futures/README.md), [`webhook`](src/main/java/com/study/principles/concurrency/events/webhook/README.md) |
| Snapshot check in the UI | `isVisible()`, `textContent()` then `assertThat` | retrying assertions | web-first assertions; arm the waiter before the click | [`events/ui`](src/main/java/com/study/principles/concurrency/events/ui/README.md) |
| "Concurrency test" that never collides | `parallel()`, `supplyAsync` loop without a gate, per-call asserts | race testing | starting gate, own pool of N, invariant across all outcomes | [`race`](src/main/java/com/study/principles/concurrency/race/README.md) |

## 2. Test data, fixtures, lifecycle

| Smell | How to spot it | Principle | Instead | Kata |
|---|---|---|---|---|
| Shared mutable template | `static Map<..>` / `static List<..>` in test data; passes alone, fails in the suite | isolation, DAMP | immutable values + builder per test | [`dry`](src/main/java/com/study/principles/dry/README.md), [`parallel`](src/main/java/com/study/principles/parallel/README.md) |
| `BaseTest` god class | many fields, an API test launches a browser, `super.setUp()` discipline, a subclass `setUp` → NPE | composition over inheritance | one JUnit 5 extension per resource, meta-annotations | [`inheritance/basetest`](src/main/java/com/study/principles/inheritance/basetest/README.md) |
| Shared client state between parallel tests | `static RequestSpecification`, `static WebDriver`, `RestAssured.baseURI =`, `ThreadLocal` everywhere | share only the immutable | immutable client, resource per test, explicit passing | [`parallel`](src/main/java/com/study/principles/parallel/README.md) |
| Cleanup as a flag or forgotten | `cleanup` parameter, `@AfterEach` copied per class, leftovers after a failure | lifecycle owns resources | registry of created data + extension | [`kiss`](src/main/java/com/study/principles/kiss/README.md), [`coupling`](src/main/java/com/study/principles/coupling/README.md) |
| Branches lose test context | `supplyAsync` on the common pool, MDC / `ThreadLocal` empty in branches | explicit context | own executor, context carried in, one deadline | [`fanout`](src/main/java/com/study/principles/concurrency/fanout/README.md) |

## 3. Page objects and UI

| Smell | How to spot it | Principle | Instead | Kata |
|---|---|---|---|---|
| Page does everything | page imports `java.sql` / REST client, `expected*` inside pages, `System.out` in every method; a redesign breaks API+DB tests | SRP | one class per reason: page, precondition, oracle, repository, reporting decorator | [`srp`](src/main/java/com/study/principles/srp/README.md) |
| Split by technical role | `CheckoutLocators` / `Waits` / `Actions` / `Verifications` + a `Flow` gluing them | SRP over-applied (low cohesion) | one page class per page | [`srp`](src/main/java/com/study/principles/srp/README.md) |
| Page tree by features | `PageWithSidebarAndSearch`, `Mobile*Page` per leaf, a page method that cannot work here | composition | components (`Header`, `Sidebar`) + one device decision | [`inheritance/pages`](src/main/java/com/study/principles/inheritance/pages/README.md) |
| Page creates its browser | `new Browser()` / `DriverManager.get()` inside a page; two browsers, lost login | coupling | constructor injection, composition root | [`coupling`](src/main/java/com/study/principles/coupling/README.md) |
| Getter per leaf | `OrderView.getShippingAddressCity()` for every field | Demeter over-applied | return a data object (DTO) and navigate it | [`demeter`](src/main/java/com/study/principles/demeter/README.md) |

## 4. Clients, drivers, interfaces

| Smell | How to spot it | Principle | Instead | Kata |
|---|---|---|---|---|
| Shallow helper | `HttpHelper.post(url, json)`, methods whose body is one delegating call, `String.format("{\"..` | deep modules | `UserApi.create(NewUser)`: hide transport, JSON, errors | [`deep`](src/main/java/com/study/principles/deep/README.md) |
| Order of calls is a convention | `getLastResponse()`, `login()` must precede `create()` | temporal coupling | return results; enforce order by types (`AuthenticatedSession`) | [`deep`](src/main/java/com/study/principles/deep/README.md) |
| Fat interface with stubs | 10+ methods, `UnsupportedOperationException` in drivers, a 13-method fake for a 1-method client | ISP | role interfaces from the clients' side | [`isp`](src/main/java/com/study/principles/isp/README.md) |
| Subtype that refuses or ignores | `ReadOnlyClient extends Client` throwing, silent no-op, `instanceof` patches | LSP | split by capability (`DbReader` / `DbWriter`), contract tests | [`lsp`](src/main/java/com/study/principles/lsp/README.md) |
| Fake that is more lenient than the real thing | fake without uniqueness, returns stored instances; tests green, prod red | LSP for fakes | one contract test for the fake and the real implementation | [`lsp`](src/main/java/com/study/principles/lsp/README.md) |
| Interface per method | `Clicker`, `Typer`, `TextReader`; a page takes three of them | ISP over-applied | name the role (`Interaction`) | [`isp`](src/main/java/com/study/principles/isp/README.md) |
| Client as a record | record component `WebDriver`, `RequestSpecification`, `token`; secrets in `toString` | records = transparent data | final class; records for DTOs and values | [`records`](src/main/java/com/study/principles/records/README.md) |

## 5. Framework structure and extension

| Smell | How to spot it | Principle | Instead | Kata |
|---|---|---|---|---|
| The same switch in several places | `switch (browser` / `equals("chrome")` / `env.equals(` in N files; a `default ->` that returns | OCP | strategy + registry (fail fast), environments as data | [`ocp`](src/main/java/com/study/principles/ocp/README.md) |
| Boolean-flag step | 2+ `boolean` parameters, call sites `true, false, true`; impossible combinations accepted | KISS | intention methods, enum, types that allow only valid next steps | [`kiss`](src/main/java/com/study/principles/kiss/README.md) |
| Flexibility for later | interface with one implementation, factory with one used branch, "planned" placeholders, `default` methods "for future impls" | YAGNI | plain class; extract the interface from two real consumers | [`kiss`](src/main/java/com/study/principles/kiss/README.md) |
| One helper for things that only looked alike | a shared helper grows a flag per difference (`isCompany`) | wrong abstraction | inline back, separate clients | [`dry`](src/main/java/com/study/principles/dry/README.md) |
| Service locator / hidden globals | `getInstance()`, `static .*Driver`, `getDriver()` outside the root | DI | constructor injection, composition root in an extension | [`coupling`](src/main/java/com/study/principles/coupling/README.md) |
| Captive dependency | a singleton holds a per-test resource (steps keep the first test's browser) | lifetimes | per-test instance, or pass the resource per call | [`coupling`](src/main/java/com/study/principles/coupling/README.md) |
| `Helper` / `Utils` / `Manager` | class names that say "everything" | SRP / cohesion | name by the reason to change | [`srp`](src/main/java/com/study/principles/srp/README.md) |

## 6. Values, state, invariants

| Smell | How to spot it | Principle | Instead | Kata |
|---|---|---|---|---|
| Mutable config with getters/setters | `getBaseUrl() + "/users"`, `System.getProperty(` outside the config, `int timeout` | encapsulation / abstraction | immutable config with intent-level methods (`endpoint(path)`, `timeout()` as `Duration`) | [`encapsulation`](src/main/java/com/study/principles/encapsulation/README.md) |
| Getter leaks a mutable collection | `return items;` from a private list | invariants | `List.copyOf`, unmodifiable views | [`invariants/leak`](src/main/java/com/study/principles/invariants/leak/README.md) |
| Mutable object as a map key | key mutated after `put`, entry unreachable | invariants | immutable keys (records of immutables) | [`invariants/hashkey`](src/main/java/com/study/principles/invariants/hashkey/README.md) |
| Setter per field | `setStatus`, `setFinishedAt` callable in any order; invalid states reachable | invariants | domain operations (`start()`, `record(..)`, `finish()`), checks in the constructor | [`invariants/testrun`](src/main/java/com/study/principles/invariants/testrun/README.md), [`invariants/retry`](src/main/java/com/study/principles/invariants/retry/README.md) |
| Record that is only shallowly immutable | `List` component without `copyOf`, `byte[]`, `password` in `toString` | records | compact constructor copies, override `toString` / `equals` | [`records`](src/main/java/com/study/principles/records/README.md), [`invariants/record`](src/main/java/com/study/principles/invariants/record/README.md) |
| Format and type traps | `YYYY`, `hh:mm`, `SimpleDateFormat`, `LocalDateTime` for a moment, `ZoneOffset.of` | time types | `uuuu`, `HH`, `DateTimeFormatter`, `Instant` / `ZonedDateTime` | [`time`](src/main/java/com/study/principles/time/README.md) |
| Listener state on a foreign thread | `ArrayList` filled by a listener, unsubscribe of a new lambda instance | thread safety | `BlockingQueue`, `pollMatching`, unsubscribe the same instance | [`events/kafka`](src/main/java/com/study/principles/concurrency/events/kafka/README.md) |

## 7. Not smells (the "When NOT to apply" sections in short)

| Looks like a smell | Why it is fine | Kata |
|---|---|---|
| A `switch` / `if (mobile)` in **one** place, two stable variants | one edit point; abstract at the third variant (rule of three) | [`ocp`](src/main/java/com/study/principles/ocp/README.md) |
| Chains on builders, AssertJ, RestAssured, DTOs | fluent APIs return the same object or data, no foreign internals | [`demeter`](src/main/java/com/study/principles/demeter/README.md) |
| `new` for values: `new NewUser(..)`, `Duration.ofSeconds(5)` | values have no lifetime or alternatives | [`coupling`](src/main/java/com/study/principles/coupling/README.md) |
| Repeated story in tests | each test states its data and expectation: DAMP | [`dry`](src/main/java/com/study/principles/dry/README.md) |
| A flag that is a value (`headless`, `timeout`) | a parameter of one behaviour, no combinations | [`kiss`](src/main/java/com/study/principles/kiss/README.md) |
| An abstract class with one shallow level that owns an invariant | Template Method: the base enforces the lifecycle | [`inheritance/session`](src/main/java/com/study/principles/inheritance/session/README.md) |
| A facade that touches API, UI and DB | orchestration is its one responsibility, as long as it delegates | [`srp`](src/main/java/com/study/principles/srp/README.md) |
| A "deep" method you need to bypass in negative tests | add `tryCreate` next to `create`, don't make everything shallow | [`deep`](src/main/java/com/study/principles/deep/README.md) |
| A cheap seam needed now (constructor injection, `Clock`) | not YAGNI: tests use it today | [`kiss`](src/main/java/com/study/principles/kiss/README.md), [`time`](src/main/java/com/study/principles/time/README.md) |

## 8. Principles that pull against each other

| Tension | Over-applied looks like | Balance | Kata |
|---|---|---|---|
| DRY ↔ DAMP | universal scenario, shared template | share mechanics, keep the story | [`dry`](src/main/java/com/study/principles/dry/README.md) |
| SRP ↔ cohesion | a class per technical role | split by actor, keep what changes together | [`srp`](src/main/java/com/study/principles/srp/README.md) |
| OCP ↔ YAGNI | strategy + registry + plugins for two variants | extend when the second switch or third variant appears | [`ocp`](src/main/java/com/study/principles/ocp/README.md), [`kiss`](src/main/java/com/study/principles/kiss/README.md) |
| ISP ↔ simplicity | an interface per method | roles named from clients' use | [`isp`](src/main/java/com/study/principles/isp/README.md) |
| Deep ↔ flexible | negative tests cannot send a bad request | a second, explicit entry point | [`deep`](src/main/java/com/study/principles/deep/README.md) |
| Demeter ↔ data | a getter per leaf | objects tell, data structures are navigated | [`demeter`](src/main/java/com/study/principles/demeter/README.md) |
| DI ↔ KISS | a container for five classes | a hand-written root until scopes and profiles need more | [`coupling`](src/main/java/com/study/principles/coupling/README.md) |

## 9. One grep pass over a framework

Run from the framework's root; each hit is a question, not a verdict. The row in the tables above says which.

```
# structure: switches, flags, speculative types, god names
grep -rnE 'switch \((browser|env)|equals\("chrome"\)|env\.equals\(' --include='*.java'
grep -rnE '\(([^)]*boolean[^)]*){2,}\)' --include='*.java'   # one-line signatures only; the IDE finds the rest
grep -rnE 'class \w*(Helper|Utils|Manager)\b|UnsupportedOperationException|instanceof \w*Client' --include='*.java'
# coupling and shared state
grep -rnE 'getInstance\(|getDriver\(\)|static .*(Driver|RequestSpecification|Map<|List<)|RestAssured\.(baseURI|filters)|ThreadLocal' --include='*.java'
# tests: layers, assertions, tautologies
grep -rnE 'import (java\.sql|io\.restassured|com\.microsoft\.playwright)' --include='*Test.java'
grep -rnE 'assertTrue\(.*equals|RETURNS_DEEP_STUBS|\.get[A-Z]\w*\(\)\.get[A-Z]\w*\(\)\.' --include='*.java'
# time and waiting
grep -rnE 'Thread\.sleep|while \(true\)|catch \(Exception|now\(\)|currentTimeMillis|SimpleDateFormat|YYYY|hh:mm|\.join\(\)' --include='*.java'
```

## Principle → kata index

| Principle / topic | Kata |
|---|---|
| Class invariants | [`invariants`](src/main/java/com/study/principles/invariants/README.md) |
| Encapsulation / abstraction | [`encapsulation`](src/main/java/com/study/principles/encapsulation/README.md) |
| Records | [`records`](src/main/java/com/study/principles/records/README.md) |
| Time | [`time`](src/main/java/com/study/principles/time/README.md) |
| Parallel tests, shared state | [`parallel`](src/main/java/com/study/principles/parallel/README.md) |
| Concurrency in tests | [`concurrency`](src/main/java/com/study/principles/concurrency/README.md) |
| Deep vs shallow, temporal coupling | [`deep`](src/main/java/com/study/principles/deep/README.md) |
| Coupling, DI, composition root | [`coupling`](src/main/java/com/study/principles/coupling/README.md) |
| Law of Demeter | [`demeter`](src/main/java/com/study/principles/demeter/README.md) |
| Inheritance vs composition | [`inheritance`](src/main/java/com/study/principles/inheritance/README.md) |
| SRP · OCP · LSP · ISP · DIP | [`srp`](src/main/java/com/study/principles/srp/README.md) · [`ocp`](src/main/java/com/study/principles/ocp/README.md) · [`lsp`](src/main/java/com/study/principles/lsp/README.md) · [`isp`](src/main/java/com/study/principles/isp/README.md) · [`dip`](src/main/java/com/study/principles/dip/README.md) |
| DRY / DAMP | [`dry`](src/main/java/com/study/principles/dry/README.md) |
| KISS / YAGNI | [`kiss`](src/main/java/com/study/principles/kiss/README.md) |
