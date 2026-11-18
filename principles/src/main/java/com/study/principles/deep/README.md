# Deep vs shallow modules, temporal coupling

A module is worth its interface when it hides much more than it exposes (Ousterhout, *A Philosophy of
Software Design*): **value = functionality hidden − cost of the interface**. A *shallow* module
(`HttpHelper`, a pass-through `UserSteps`) costs as much to learn as it saves — every caller still knows
HTTP. **Temporal coupling** is a hidden "call A before B" rule: the interface accepts any order,
only one of them works.

| Package | What to look at |
|---|---|
| `bad` | `HttpHelper`: a mutable request-in-progress (`setHeader` → `setBody` → `post` → `getStatus`). `UserSteps`: a layer without depth — `login()` must come first and mutates the shared helper, JSON via `String.format`, status never checked, `getStatus()`/`getResponseBody()` are pass-throughs |
| `good` | `UserApi.create(NewUser) → User`: headers, escaping, expected status, error translation, id parsing — all inside. `tryCreate` → raw `HttpResponse` for negative tests. `AuthApi.login → AuthenticatedSession` (package-private constructor) is required by `UserApi`'s constructor, so the order is enforced by types |
| root | `FakeUsersBackend` — a strict in-memory users API (401/415/400/403/409/201), records requests. `Json` — minimal flat JSON. `DeepDemo` runs the same scenarios through both designs |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.deep.DeepDemo
mvn -q -pl principles test -Dtest='BadUserStepsTest,GoodUserApiTest,UserApiPropertyTest'
```

- `BadUserStepsTest` — characterization: forgotten login, invalid email and a quote in the name all end in the
  same `NumberFormatException: Cannot parse null string`; a token leaks into the "next test"; a GET carries
  the previous POST's body.
- `GoodUserApiTest` — the same scenarios; failures read `POST /api/users as admin: expected 201 but was 400,
  body: {"error":"invalid email"}`.
- `UserApiPropertyTest` (jqwik) — "any name round-trips". Statable only because serialization lives in one
  module; the companion property shows the shallow steps never round-trip a name that needs escaping.

## Depth, measured on one call

| What the test author must know | bad (`HttpHelper` + `UserSteps`) | good (`UserApi`) |
|---|---|---|
| Path, method | yes (helper) / no (steps) | no |
| `Content-Type`, `Authorization` format | yes / hidden in `login()` side effect | no |
| Call order | `login` before `createUser`, `post` before `getStatus` | none — enforced by constructor |
| JSON escaping | nobody does it | `UserApi` |
| Which status means success | nobody checks | `UserApi` (201) |
| Where the error is | `catch (NumberFormatException)` + `getStatus()` | in the exception message |
| State left for the next call | headers, body, last response | none |

## Kinds of temporal coupling and the fixes

| Coupling | Fix | In this kata |
|---|---|---|
| "Log in before calling the API" | Make the proof of the first step a **type** the second step requires | `AuthenticatedSession` → `new UserApi(session)` |
| "Set headers/body before send" | One method takes everything a call needs; build the request from scratch per call | `create(NewUser)` |
| "Read the response only after send" | Return the result instead of storing it | `create` returns `User`, `tryCreate` returns `HttpResponse` |
| "init() before use", "open() before click" | Do it in the constructor / factory; or a **step builder** whose each step returns the next interface | not shown — see exercise 3 |

## Too deep is also a smell
If `UserApi` had only `create` (throws on non-201), every negative test of `/api/users` would bypass it and
go back to raw HTTP. Hence two levels: `create` for preconditions (most tests), `tryCreate` for testing the
endpoint itself. A deep module should make the common case trivial **and** the rare case possible.

## Pros / cons

| Pros | Cons |
|---|---|
| Tests read like requirements; one line per precondition | More classes (`NewUser`, `User`, `ApiException`, session) |
| HTTP details change in one place (header name, status, JSON lib) | The deep module must be designed: wrong defaults hurt every test |
| Errors are translated at the boundary — the failure message is the bug report | Hiding too much forces bypasses (see "too deep") |
| No shared mutable state → parallel-safe, no leaks between tests | Type-enforced order adds a type per state; overkill for one-off code |
| Properties like "any name round-trips" can be stated and tested | |

## When NOT to apply
- **The test IS about HTTP** — contract tests for headers, content negotiation, status codes. There the
  "shallow" level (`tryCreate`, raw RestAssured) is the right tool; don't force it through a domain API.
- **One call site** — a one-off script hitting an endpoint once does not need `UserApi`; inline RestAssured
  is deeper than a wrapper nobody reuses.
- **Wrapping an already deep library** — `HttpHelper` over RestAssured is shallower than RestAssured
  itself (it loses `given/when/then`, filters, specs). Wrap at the domain level (`UserApi`), not at the
  transport level.
- **Splitting into tiny classes "for SRP"** — `UserRequestBuilder` + `UserRequestSender` + `UserResponseParser`,
  each a pass-through, is "classitis": many shallow modules instead of one deep one.

## Try it yourself
1. The SUT starts returning `200` instead of `201` from `POST /api/users`. How many places change in `bad`
   (count the tests that check the status)? In `good`?
2. Add `X-Request-Id` to every request. Where does it go in each design, and what happens in `bad` if
   one test forgets to set it?
3. Replace `AuthenticatedSession` with a step builder: `UsersFlow.as(admin).create(user)` where `as(..)`
   returns an interface that has `create` and the root has only `as`. Compare with the session type —
   which one composes better with several APIs (`OrderApi`, `UserApi`)?
4. Add `UserApi.createAll(List<NewUser>)`. Is it a deeper module or just a wider one?
   (Hint: what does it hide that a loop in the test would not?)
5. Smell check on your framework: grep for `getLastResponse`, `setHeader(` outside a client class,
   `String.format("{\"`, and methods whose body is a single delegating call.
