# DRY vs DAMP — over-dried tests, wet tests, and what to share

**DRY is about knowledge, not text**: every piece of knowledge has one authoritative place. Two identical
lines that would change for different reasons are not duplication; one rule written in two places is,
even if the lines look nothing alike. **DAMP** (Descriptive And Meaningful Phrases) is DRY's counterweight
in tests: a test is read far more often than it is changed, so its *story* — the data that matters and the
expected result — stays in the test body, even at the cost of repetition. Share the **mechanics**
(how to call, how to compare), keep the **story** local.

| Package | What to look at |
|---|---|
| `bad` | `TestData.validUser()` hands out a shared mutable template; `UserScenario.run(user, 400, "age_too_low", false)` — a universal scenario with positional meaning; `minAge()` reads the boundary from the app; `Checks.verifyUser` compares whole maps with `isTrue()`; `Checks.assertRejected(.., boolean company)` — one helper for two endpoints that diverged |
| `good` | `Users.aUser().aged(17)` — a builder with irrelevant defaults, the test states the difference; immutable `NewUser`; `UserApi` / `CompanyApi` own the paths and JSON; `ResponseAssert` — a custom AssertJ assertion whose failures name the field, the expectation and the response |
| test `bad` / `wet` / `good` | the same user tests three times: `OverDriedScenarios`, `WetScenarios` (every test spells out the map, the path and the parsing), `DampScenarios` — run by `DryTest` through EngineTestKit, not by Surefire directly |
| root | `FakeServer` (users: 400 + `error`; companies: 422 + `errors`), `Release` — builds with regressions (`withMinAge(21)`, `losingCountry()`, `withUsersPath(..)`), `Deployed` — the stand the tests reach, `DryDemo` |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.dry.DryDemo
mvn -q -pl principles test -Dtest='DryTest'
```

`DryTest` deploys good and broken builds and runs each style against them:

| Situation | over-dried (`bad`) | wet | DAMP (`good`) |
|---|---|---|---|
| Correct build, default order | `userIsCreated` fails with `expected 201 but was 400` — an earlier test blanked the name in the shared template; alone it passes | green | green, also in three random orders |
| Build with min age 21 (rule is 18) | **green**: expectations are `minAge()` and `minAge() - 1` — the test agrees with the app by construction | red (literal 18) | red: `anAdultOf18IsAccepted` |
| Build drops the country | `Expecting value to be true but was false` | `expected "DE" but was "US"` | `Expected <country> to be <DE> but was <US> in 201 {age=30, country=US, ...}` |
| Endpoint renamed to `/api/v2` | 1 edit (the path *is* shared) | **4 edits**, one per test | 1 edit in `UserApi` |

The wet column is not all bad: each test reads on its own and fails clearly. Its cost is maintenance and
noise — the one field that differs hides among four `put`s. The over-dried column saved lines and lost
the two things a test exists for: catching a regression and explaining a failure.

## What to share, what to keep in the test

| Share (DRY) — knowledge about *how* | Keep in the test (DAMP) — the *story* |
|---|---|
| Endpoint paths, selectors, SQL — the app's interface (`UserApi`, page objects) | The data that makes this case: `aged(17)`, `named("")` |
| Serialization, auth, retries — transport | The expected value, as a **literal**: `18`, `"age_too_low"` |
| How to compare and report (`ResponseAssert`, `Checks` with good messages) | Which checks matter for this case (`has("country", "DE")`), not "verify everything" |
| Valid defaults nobody cares about (builder) | Setup that the assertion depends on — visible above it, not in a base class |
| A business rule as an oracle used by many tests ([`srp`](../srp/README.md): `ExpectedPrice`) — written independently of the app | A boundary taken from the app's own constant: that is not DRY, it is a tautology |

Rule of thumb: if a reader must open another file to understand *why the test expects what it expects*,
something from the story was dried away. If a change of the app's interface needs an edit per test, the
mechanics were not.

## Smells

- Helpers with positional booleans and nulls: `run(user, 400, null, false, true)` (flags: [`kiss`](../kiss/README.md)).
- A shared `static` map/object that tests modify; tests that pass alone and fail in the suite.
- Expectations computed from production code or config: `MIN_AGE - 1`, `price * TAX_RATE` copied from the app.
- `assertTrue(actual.equals(expected))`, `verifyAll(response)` — failures that need a debugger.
- A base class or `@BeforeEach` that builds data for all tests, each using a different part of it.
- One helper serving two endpoints, growing a flag per difference — the abstraction was wrong, inline it back.
- The other way: every test repeats the path, the headers and the JSON parsing.

## Pros / cons of DAMP tests

| Pros | Cons |
|---|---|
| A test is understood without leaving its body; the failure explains itself | More lines; reviewers see "duplication" and want to extract it |
| Literal expectations catch regressions in the rule itself | A real rule change means editing each literal (that is the test doing its job) |
| Immutable data + builders → no order dependence, safe in parallel ([`parallel`](../parallel/README.md)) | Builders and custom assertions are code to maintain |
| Mechanics in one place → interface changes are one edit | Needs judgement on the line between story and mechanics |

## When NOT to apply (DRY in tests is right)
- **The mechanics** — paths, selectors, JSON, auth: never repeat them per test; that is the wet column.
- **A truly shared fixture that is read-only and expensive** — a reference dataset loaded once; fine if
  immutable and no test depends on what another test did with it.
- **Parameterized tests** — `@ParameterizedTest` over a table of `(age, expected)` is DRY *and* DAMP: the
  table is the story. Prefer it to a loop or a universal helper.
- **An independent oracle** — computing the expected value is fine when the computation is written in the
  test domain and checked against examples (`srp`: `ExpectedPrice` + jqwik), not copied from the app.
- **Production code** — DAMP is a test-code idea; in the framework itself duplicated knowledge is a bug
  waiting to happen. But the rule of three holds there too: two similar pieces are not yet a pattern ([`kiss`](../kiss/README.md)).

## Try it yourself
1. Add a test "email without `@` is rejected" in all three styles. Count what you touched in each.
2. Make `TestData.validUser()` return a copy. Which `DryTest` assertion changes, which over-dried problems
   remain?
3. Replace `minAge()` in `OverDriedScenarios` with literals. Which column of the table moves?
4. Rewrite the age tests in `DampScenarios` as one `@ParameterizedTest` with a `@CsvSource` of
   `age, expected`. Is it still DAMP? What does a failure message look like?
5. The companies team adds a third difference (errors as objects `{field, code}`). Add it to
   `Checks.assertRejected` and to `good`. Which one forced every caller to change?
6. Smell check on your suite: grep for `static .*Map<` and `static .*List<` in test data classes,
   `assertTrue(.*equals`, helpers with more than two `boolean` parameters, and imports of production
   constants in tests.
