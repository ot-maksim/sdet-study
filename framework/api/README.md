# framework/api

REST clients of the SUT on RestAssured. Two layers: a transport that knows how to talk HTTP (`ApiClient`) and
resource clients that know the API's meaning (`UserApi`). Tests and steps see neither RestAssured nor JSON.

| Class | What to look at |
|---|---|
| `ApiSettings` | `api.base-url`, `api.timeout` from this module's `framework.properties`; `config.bind(ApiSettings::from)` |
| `ApiClient` | immutable spec built once, `given().spec(spec)` per call, no RestAssured statics; own Jackson mapper (tolerant reader); an unreachable stand → one message with the URL and "docker compose up" |
| `ApiResponse` | status, body, `Location`, plus the request — `toString()` is the whole exchange; `expect(201)`, `as(User.class)`, `error()`, `isError(404, "user_not_found")` |
| `ApiResponseAssert` | `assertThat(response).isRejectedWith(400, "age_too_low")`: compares status + code, never the human message; fails with the exchange |
| `users.UserApi` | intentions: `create`, `get`, `find` (empty only for `404 user_not_found`), `findByEmail`, `all`, `delete`, `deleteIfExists` (idempotent, for cleanup); `tryCreate` / `tryCreateRaw` for negative tests |
| `users.NewUser` / `User` / `NewUsers` | request DTO with withers, response DTO, valid unique requests: `newUsers.valid().withAge(17)` |

Tests: `mvn -q -pl framework/api -am test`. The real SUT runs in-process (`InProcessSut`: in-memory storage,
free port), so the client is checked against the actual contract — no Docker, no hand-written HTTP mock.

## How a test reads

```java
User ada = users.create(newUsers.valid());                                     // happy path: DTO or a loud failure
assertThat(users.tryCreate(newUsers.valid().withAge(17))).isRejectedWith(400, "age_too_low");
```

A failure explains itself:

```
Expected 201 but got:
  POST /api/users {"name":"Ada k3x9q-1","email":"ada-k3x9q-2@example.test","age":17,"country":"DE"}
  -> 400 {"error":"age_too_low","message":"Age must be at least 18"}
```

## Decisions

[ADR 0003 — API client: transport + deep resource clients, own response type](../../docs/adr/0003-api-client.md)

## Pros / cons

| + | − |
|---|---|
| One place per resource for paths, statuses, parsing; an API change is one edit | Two methods per operation (`create` / `tryCreate`) — the price of readable negative tests |
| RestAssured is an implementation detail: replaceable by `java.net.http` without touching tests | RestAssured's fluent `then().body(...)` matchers are not available to tests — on purpose |
| Failures carry the request and the response | Request bodies appear in messages: a resource with secrets must mask them in `ApiResponse.toString()` |
| Safe for parallel runs: no shared mutable spec, no statics | — |

## When NOT to do this

- Exploratory or contract tests of the API itself (headers, every status, schema) — there the raw RestAssured DSL
  *is* the readable form; wrapping it hides what is being checked.
- A one-off script against one endpoint: `given().get(...)` is fine.

## Try it yourself

1. Add `UserApi.update` for a `PATCH` the SUT does not have yet: what does `expect(200)` print against today's SUT?
2. Make `find` return empty for any 404 and point the client at a wrong base path (`/api/v2`) — which test lies now?
3. Replace RestAssured in `ApiClient` with `java.net.http.HttpClient`. How many files outside `ApiClient` change?
