# framework/domain

Business steps over the clients — what a scenario says, not how a channel works. The only layer that depends on
`api`, `db` and `ui` together, and the only one that relates their views of the same user.

| Class | What to look at |
|---|---|
| `UserPrecondition` | "there is a user": `ApiUserPrecondition` (through the SUT) / `DbUserPrecondition` (direct insert); the root picks one by `domain.user-precondition` (DIP) |
| `UserSteps` | facade: `existing()` / `existing(u -> u.withCountry("GB"))` — channel irrelevant; `registerThroughApi`, `registerThroughUi`, `tryRegisterThroughApi` — channel is the subject; everything is cleaned up |
| `UserChecks` | `assertStoredAsReturned` (API vs table), `assertListed` (API vs page), `assertNotStored` |
| `DomainSettings` | `domain.user-precondition` = `api` (default) or `db` |

Tests: `mvn -q -pl framework/domain -am test` against the compose stand (skipped with the reason when it is down).
`UserPreconditionContract` runs for both preconditions; `UserStepsTest` checks that every channel's users are gone
after the test, including one created through the UI and a rejected one.

## Precondition vs action

```java
User ada = users.existing();                          // precondition: how is the root's business
User bob = users.registerThroughApi(users.aNewUser()); // action under test: the channel is the point
```

A test about the page's delete button does not care whether its user came through HTTP or SQL; a test about
registration through the form does. Mixing the two makes every test pay for the slowest channel, or hides the
channel a test is actually about.

## The DB precondition's price

`DbUserPrecondition` bypasses the SUT, so it repeats the SUT's normalization (trim, lower-case email, upper-case
country, microsecond timestamp) — **a copy of knowledge**, which DRY warns about. Accepted because the contract
test compares its users with API-created ones: drop `toLowerCase` and `userLooksAsIfTheApiHadCreatedIt` fails.
Without that contract the copy would drift silently and tests would run against states production never has.

## Decisions

[ADR 0006 — domain layer: steps facade, preconditions behind an interface, cross-channel checks](../../docs/adr/0006-domain-layer.md)

## Pros / cons

| + | − |
|---|---|
| Scenarios read as business steps; channels change in one place | One more layer to learn; trivial steps are pure pass-through (keep them out) |
| Precondition speed is a config switch, not a test rewrite | The DB channel duplicates SUT rules — guarded by the contract, still a cost |
| Cleanup for every channel, including UI and "should not have been created" | Cleanup by email lookup for UI creations — one extra API call |

## When NOT to do this

- Few tests, one channel: tests can call `UserApi` directly; a facade over one client is shallow.
- Steps that only forward one call (`users.get(id)` → `api.get(id)`): use the client.
- A "verify" flag inside a step — checks belong to the test or `UserChecks` (principles/kiss).

## Try it yourself

1. Switch `domain.user-precondition` to `db` (env file or `DOMAIN_USER_PRECONDITION=db`) and time the tests module.
2. Make the SUT keep emails as typed; which test of which module fails first?
3. Add `existing(int count)` for a list test. Which precondition would you pick for 1000 users, and why?
