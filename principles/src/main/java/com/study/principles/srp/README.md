# SRP — Single Responsibility Principle

**A module should have one reason to change**, i.e. answer to one *actor*: one source of change requests.
It is not "a class does one thing" — `CheckoutPage` with ten methods is fine if all ten change when the
checkout markup changes. The test is: *who would ask me to change this line?* Two different answers in one
class → split along that line. The same answer in five classes → you split too far (low cohesion).

| Package | What to look at |
|---|---|
| `bad` | `CheckoutPage` with selectors + loaded-check in the constructor, `prepareCart` via API, a private copy of the pricing rule in `verifyTotal`, `assertOrderInDb`, `System.out` reporting with the card in clear, `randomCard()` |
| `good` | one class per reason: `CheckoutPage` (markup), `CartPrecondition` (setup channel), `ExpectedPrice` (pricing oracle), `Orders` (DB), `ReportingBrowser` + `StepReporter` (reporting, as a decorator), `Cards` (test data) |
| `overapplied` | the page cut by technical role: `CheckoutLocators`, `CheckoutWaits`, `CheckoutActions`, `CheckoutVerifications` — and `CheckoutFlow` gluing them back together |
| root | `FakeShop` (carts, pricing, orders), `FakeBrowser` with `Markup.V1` / `V2` (redesign: `data-test` selectors), `ShopApi`, `ShopDb`, `SrpDemo` |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.srp.SrpDemo
mvn -q -pl principles test -Dtest='SrpTest,ExpectedPriceProperties'
```

- `SrpTest` — **bad**: after the redesign an API + DB test breaks, because the DB helper lives in a page whose
  constructor checks the markup; reporting can only be caught by hijacking `System.out`, and it prints the
  card. **good**: the redesign breaks only the UI test; the test records steps through the decorator (card
  masked); the oracle is a function with example values.
- `ExpectedPriceProperties` — jqwik: the oracle agrees with the shop's API for 1000 random carts in well under a
  second. In `bad` the rule is private in the page: each check would need a browser.

## Reasons to change in a test framework

| Reason (actor) | Lives in | Smell when it leaks |
|---|---|---|
| Markup / UX (front-end) | page objects, components | selectors in steps or tests |
| Business rules (product) | oracles, expected-value builders | `expectedTotal()` inside a page or a client |
| Setup channel (API / DB / fixtures) | preconditions ([`dip`](../dip/README.md)) | page objects calling the API |
| Persistence schema (back-end) | DB repositories | SQL in tests or pages |
| Reporting / logging (infra) | decorators, listeners, extensions | `log()` / `Allure.step()` in every method |
| Test data conventions (QA) | builders, data factories | `random*()` in pages |
| Environment (devops) | config, composition root ([`coupling`](../coupling/README.md)) | URLs and timeouts in pages |

## SRP vs cohesion
SRP splits what changes for *different* reasons; cohesion keeps together what changes for the *same* reason.
`overapplied` breaks the second: a promo-code field touches a locator, a wait, an action and a read — four
files for one change, and `CheckoutFlow` exists only to undo the split. In `good` it is one class.

| Change | `bad` | `good` | `overapplied` |
|---|---|---|---|
| Redesign (selectors) | `CheckoutPage` — and every API/DB test using it breaks meanwhile | `CheckoutPage` | `CheckoutLocators` |
| New field on the form | `CheckoutPage` | `CheckoutPage` | 4 classes + `CheckoutFlow` |
| VAT 20% → 21% | `CheckoutPage` (UI-only access to the rule) | `ExpectedPrice` | (oracle elsewhere) |
| Allure instead of stdout, mask cards | every method of every page | `StepReporter` impl / `ReportingBrowser` | every page |
| Orders table renamed | `CheckoutPage` | `Orders` | — |

## Pros / cons

| Pros | Cons |
|---|---|
| A change lands in one place and breaks only tests that depend on it | More classes; a reader follows names instead of one file |
| Pieces are reusable across channels (the oracle in UI, API and DB tests) | Wrong axis of splitting is worse than none (`overapplied`) |
| Pure pieces (oracle) get cheap, exhaustive tests | Need a place that wires the pieces (root / extension / facade) |
| Cross-cutting concerns (reporting, masking) decided once | Decorators hide behaviour: a step is logged by a class the page never mentions |

## When NOT to apply
- **The pieces always change together** — locators and actions of one page; a DTO and its builder. Splitting
  them is the `overapplied` package.
- **Early, small, one owner** — a five-test suite written by one person against a stable app: one page class
  with a helper method is fine; split when the second actor shows up (the first redesign, the first DB check).
- **Speculative actors** — "someone might want Allure some day" is YAGNI; split when the second reporter exists
  ([`kiss`](../kiss/README.md): the reporter interface guessed before the second consumer).
- **A facade on purpose** — a domain step like `checkout.buy(user, cart)` *coordinates* API, UI and DB; that is
  its one responsibility (orchestration), as long as it delegates and holds no selectors, SQL or rules itself.

## Try it yourself
1. Fix `good` for markup V2. How many files changed? Do the same in `bad` — and note which unrelated tests
   were red until you did.
2. Change VAT to 21% in `FakeShop`. Which test catches it in `good`, and how fast? What would catch it in `bad`?
3. Add a promo-code field (`#promo`, reduces the total by 5) in all three packages. Count the files.
4. Write an `AllureLikeReporter` that groups steps by test name. Where does the test name come from without the
   page knowing about it? (Hint: an extension, see `inheritance/basetest`.)
5. SRP for an API client: a `UserClient` that builds requests, refreshes the token, retries on 503, maps JSON,
   and logs. List the actors; sketch the split (hint: RestAssured filters are decorators).
6. Smell check on your framework: page objects importing `java.sql` or a REST client, `assert`/`expected` inside
   pages, `System.out`/logger calls in every page method, classes whose name ends in `Helper`/`Utils`/`Manager`.
