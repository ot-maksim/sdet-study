# Records in test automation: where they fit, their traps, where they don't

A `record` is a **transparent, shallowly immutable carrier of data**: you declare the components, the compiler
generates `private final` fields, a canonical constructor, accessors and `equals`/`hashCode`/`toString`
over *all* components. Its public API **is** its state — great for values, wrong for objects that must hide
anything.

Validation in the compact constructor and defensive copies of `List` components are covered in
[`invariants/record`](../invariants/record/README.md); this kata is about choosing records in a framework.

| Package | What to look at |
|---|---|
| `fit/bad` | `UserResponse`: hand-written DTO, `role` added later but forgotten in `equals`/`toString` → false-positive `isEqualTo` |
| `fit/good` | the same DTO as a one-line record |
| `traps/bad` | `Credentials`: generated `toString` prints the password. `Attachment`: `byte[]` component compared and printed by reference |
| `traps/good` | `Credentials` overrides only `toString`. `Attachment` copies in/out and overrides `equals`/`hashCode`/`toString` |
| `misfit/bad` | `UsersApiClient` as a record: public `token()`, token in `toString`, mutable history as a public component, hash changes after each request |
| `misfit/good` | `UsersApiClient` as a final class: behaviour public, internals private, identity equality |
| root | `RecordsDemo` runs every pair |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.records.RecordsDemo
mvn -q -pl principles test -Dtest='UserResponseEqualityTest,RecordsInTestsTest,RecordTrapsTest,AttachmentPropertyTest,ApiClientMisfitTest'
```

- `UserResponseEqualityTest` — the bad DTO passes an assertion it should fail; the record fails with a readable diff.
- `RecordsInTestsTest` — records *in test code*: a `@ParameterizedTest` case type with a readable display name,
  and a local record for comparing UI table rows as values.
- `RecordTrapsTest`, `AttachmentPropertyTest` (jqwik) — the two traps as executable facts.
- `ApiClientMisfitTest` — what goes wrong when a client is a record.

## Where records fit

| Use | Why |
|---|---|
| API request/response DTOs | equality over all fields for `isEqualTo`, Jackson 2.12+ / RestAssured `.as(..)` work via the canonical constructor |
| DB rows read by JDBC (`UserRow`) | a row is its columns; compare expected vs actual rows as values |
| Test data and parameter sets (`LoginCase`) | named, typed fields instead of `Arguments.of(...)`; `toString` = display name |
| Value objects with an invariant (`DateRange`, `Endpoint`, `RetryPolicy`) | validate once in the compact constructor, immutable afterwards |
| Map keys, `Set` elements, composite keys (`(env, browser)`) | stable `hashCode` (if components are immutable) |
| Returning several values (`Page<T>(items, total)`) | instead of `Object[]`, `Map`, or out-parameters |
| Local throwaway types in a test (`record Row(...)`) | grouping/projection without a new file |
| Config snapshots, events/results (`StepResult`) | immutable, safe to share across parallel tests |

## Traps (record fits, but needs care)
1. **`toString` prints everything** — passwords, tokens, PII land in logs, parameterized test names, reports.
   Override `toString` (or don't put the secret in a record).
2. **Arrays** — `equals`/`hashCode` by reference, `toString` prints `[B@…`, contents mutable. Override all
   three plus the accessor, or use `List`/`ByteBuffer`/a checksum.
3. **Mutable components** (`List`, `Date`, your own classes) — shallow immutability; copy in the compact
   constructor ([`invariants/record`](../invariants/record/README.md)).
4. **No validation by default** — `new DateRange(to, from)` compiles; add checks in the compact constructor.
5. **Many optional components** — `new CreateUserRequest("john", null, null, "USER", true, null, null)` is
   unreadable and order-sensitive. Keep the record as the type, add a builder or a `defaults()` factory with
   `withX(..)` methods for test data.
6. **Adding a component is a breaking change** — every `new X(...)` call site must change. Funnel creation
   through factories/builders in test data so it's one place.

## Where records do NOT fit
- **Objects with behaviour and hidden state** — API clients, page objects, components, driver/DB sessions,
  connection pools. A record exposes every component and cannot hold extra private fields (see `misfit`,
  and why `encapsulation.good.TestConfig` is a class).
- **Mutable accumulators** — a test run collecting results, a retry counter, a cookie jar
  (see `invariants/testrun`).
- **Identity-based objects** — two clients/sessions with the same fields are still two different things.
  JPA entities in particular: no no-arg constructor, no proxies, `equals` on all fields breaks Hibernate.
- **Class hierarchies** — records are `final` and cannot extend a class (`BasePage`, `BaseClient`).
  They *can* implement interfaces — `sealed interface ApiResult permits Ok, Error` with record cases is a
  good fit.
- **Partial or lazy state** — "load the field later", caches, memoized expensive values; a record has
  only final components fixed at construction.
- **JavaBeans-only libraries** — tools that require a no-arg constructor and setters (old ORMs, some
  serializers, older Jackson). Check the version before migrating DTOs.

## Pros / cons

| Pros | Cons |
|---|---|
| `equals`/`hashCode`/`toString` can't drift from the fields | All components are public — no information hiding |
| Shallowly immutable, `final` — safe as keys and across threads | Only shallow: mutable components and arrays need manual work |
| Intent is explicit: "this is data" | No extra instance fields, no inheritance, no lazy state |
| Readable failure messages out of the box | Generated `toString` leaks secrets |
| Less code to review | Positional construction gets unreadable beyond ~4–5 components |

## Try it yourself
1. Add `createdAt` to `fit.bad.UserResponse` and `fit.good.UserResponse`. Count the places you edited in each.
   Then decide: should `createdAt` take part in the equality an API test asserts on? How do you compare
   "everything except `createdAt`" (`usingRecursiveComparison().ignoringFields(..)` vs a projection record)?
2. Replace `byte[]` in `traps.good.Attachment` with `List<Byte>` or a SHA-256 checksum. Which overrides
   can you delete?
3. Write `record CreateUserRequest` with 7 components and a static `defaults()` that generates a unique
   login/email, plus `withRole(..)`, `withEmail(..)`. Compare call sites with the positional constructor.
4. Model an API result as `sealed interface ApiResult permits Ok, ClientError, ServerError` with records,
   and make a test helper that asserts `Ok` and returns its body. (On Java 17 use `instanceof` patterns;
   `switch` patterns over records need Java 21.)
5. Smell check on your real framework: grep for records whose components are `WebDriver`, `Page`,
   `Connection`, `RequestSpecification`, `List<…>` without `copyOf`, `byte[]`, or anything named `password`/`token`.
