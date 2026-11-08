# Encapsulation and abstraction

**Abstraction** decides *what* a client sees: an API phrased in the client's terms
("the URL of `/api/users/1`", "the timeout"), not in terms of the storage (`String baseUrl`, `int timeout`).
**Encapsulation** is the mechanism that keeps everything else out of reach (`private`, `final`, no getters
to internals), so the hidden decisions can change without touching clients.
It is not about security — reflection reads private fields anyway. It is about protected invariants and
the freedom to change the implementation.

Invariants themselves (validation, defensive copies, records, state machines) are covered in
[`invariants`](../invariants/README.md); this kata focuses on *what* vs *how*.

| Package | What to look at |
|---|---|
| `bad` | `TestConfig`: private fields + getter/setter per field + global singleton. `UsersApiClient` and `LoginPage` each decide HOW to use the raw values — and decide differently. `AdminSmokeTest` changes `baseUrl` mid-run |
| `good` | `TestConfig`: immutable, validated once, exposes `endpoint(path)` and `timeout(): Duration`, never the base URL. `withBaseUrl(..)` returns a copy |
| root | `FakeTransport` records what would go over the wire; `EncapsulationDemo` runs the same scenarios against both |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.encapsulation.EncapsulationDemo
mvn -q -pl principles test -Dtest='BadTestConfigTest,GoodTestConfigTest,EndpointPropertyTest'
```

- `BadTestConfigTest` — characterization tests: they pass *because* they pin the bugs. Note the
  `@AfterEach` that restores global state by hand.
- `GoodTestConfigTest` — the same scenarios, correct behaviour.
- `EndpointPropertyTest` (jqwik) — "for any slash spelling the URL is the same". Such a property can only
  be stated when URL building lives in one place.

## What is hidden in `good.TestConfig` (information hiding, Parnas)

Hide the decisions that are likely to change:

| Decision | bad: who knows it | good: who knows it |
|---|---|---|
| Trailing slash / context path | every client that concatenates | `TestConfig.endpoint` |
| Timeout unit | every client (and they disagree) | the type `Duration` |
| Property names, source (sysprops/env/file) | `TestConfig` + anyone reading `System.getProperty` | `TestConfig.from*` factories |
| Who may change values and when | anyone, anytime | nobody; overrides are copies |

## Why a class and not a record here
A record's API *is* its representation: `record TestConfig(URI base, Duration timeout)` publishes `base()`,
and clients are back to `base() + "login"`. Records are right for data (DTOs, test data, keys).
Here the goal is the opposite — hide the data behind behaviour.

## Pros / cons

| Pros | Cons |
|---|---|
| One place for parsing, normalization, validation | More code than a bag of fields |
| Representation can change (String → URI, add context path) with zero client edits | Every new need is a new method — clients cannot "just read the field" |
| Fails at startup with all problems listed | Overrides need copy methods (`withX`) or a builder |
| Immutable → safe for parallel tests, nothing to restore | Risk of a god-config: `loginPageUrl()`, `usersEndpoint()`, ... |

## When NOT to apply
- **Data crossing a boundary** — a JSON DTO or a DB row *is* its representation; a record with accessors is
  the right abstraction.
- **Throwaway scripts** — a one-off migration script does not need information hiding.
- **Over-hiding** — if clients genuinely need a value (e.g. the cookie domain), expose it as a query in their
  terms (`cookieDomain()`), don't force them to parse `endpoint("").getHost()`. An abstraction that
  fights its clients gets bypassed.
- **Too-specific methods** — `endpoint(path)` is the abstraction; `usersEndpoint()`, `loginUrl()`, ... turn
  the config into a registry of every page and break OCP. Paths belong to the clients that own them.

## Try it yourself
1. Move the SUT under a context path: `http://localhost:7070/sut`. How many files change in `bad`?
   In `good`? (Check `contextPathNeedsNoChangeInClients`.)
2. Change the timeout property from `timeout.ms=5000` to `timeout=5s`. Who has to change in each design?
3. Add `public URI baseUrl()` to `good.TestConfig` "for convenience". Write the client that will inevitably
   appear next. What did you lose, and which test could no longer protect you?
4. Make `bad.TestConfig` safe for parallel tests (`ThreadLocal`? `synchronized`?). Compare the amount of code
   and the remaining problems with `good`.
5. Smell check on your real framework: grep for `getBaseUrl() +`, `System.getProperty(` outside the config
   class, and `int timeout`.
