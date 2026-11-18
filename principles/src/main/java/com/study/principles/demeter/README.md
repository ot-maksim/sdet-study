# Law of Demeter

A method talks only to its **immediate friends**: `this`, its fields, its parameters, objects it creates.
It does not reach through a friend into the friend's friends (`a.getB().getC().doIt()`).
The practical form is **tell, don't ask**: ask an object to do the job instead of pulling out its parts
to do the job yourself. What the law protects is *hidden behaviour and structure*; it is not a dot counter.

| Package | What to look at |
|---|---|
| `bad` | `AppPage` with getters only (`getHeader()`, `getSidebar()`), components `Header` → `UserMenu` → `Dropdown` → `MenuItem` that hand out the next object. `UserScenarios` — test bodies as train wrecks: `app.getHeader().getUserMenu().open().getDropdown().getItem("Logout").click()` |
| `good` | `AppPage.logout()` / `switchLanguage(..)` / `openProfile()` — intentions; `UserMenu` is package-private and also "tell" style (`select(item)` owns open → click). Where the menu lives is known in one private method |
| `good/chains` | Long chains that are fine: `NewUser.builder()...build()` (same object), `Order` response DTO (`order.customer().address().city()` — data, not behaviour) |
| `bad/overapplied` | `OrderView`: LoD applied to data — a forwarding getter per leaf (`getCustomerAddressCity()`) |
| root | `FakeApp` — user menu in the header (`V1`) or, after a redesign, in the sidebar (`V2`). `DemeterDemo` runs everything on both |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.demeter.DemeterDemo
mvn -q -pl principles test -Dtest='RedesignTest*,ChainsThatAreFineTest'
```

- `RedesignTest` — bad: every scenario works on V1 and breaks on V2 with `no element 'header > user-menu'`;
  the structure also lets a test skip `open()`. Good: the same three actions pass on both layouts; the page
  exposes no components at all.
- `ChainsThatAreFineTest` — builder, AssertJ, DTO navigation; and `OrderView`'s one-method-per-leaf mirror.

## Violation or not?

| Chain | Verdict | Why |
|---|---|---|
| `page.getHeader().getUserMenu().open().getDropdown().getItem("Logout").click()` | **violation** | walks through objects with behaviour; the test now owns the layout and the step order |
| `driver.findElement(..).findElement(..).getText()` in a test | **violation** | the DOM path is a page object's secret |
| `config.getEnv().getDb().getConnection().createStatement()` | **violation** | four modules' internals in one line; give the caller `db.query(..)` |
| `NewUser.builder().name(..).admin().build()` | fine | every call returns the same builder — one friend |
| `assertThat(list).extracting(..).containsExactly(..)`, `given().when().then()`, `stream().map().filter()` | fine | DSLs designed to chain; each step returns the DSL's own type |
| `order.customer().address().city()` on a response DTO / record | fine | data structure: its shape *is* the published contract |
| `user.getAccount().getBalance().subtract(..)` on a domain object with rules | violation | data with behaviour behind it: `account.withdraw(..)` keeps the rule inside |

Objects vs data structures (Clean Code, ch. 6): objects hide data and expose behaviour — LoD applies; data
structures expose data and have no behaviour — LoD does not. Hybrids (DTOs with logic) get the worst of both.

## Links to other katas
- [`encapsulation`](../encapsulation/README.md): getters to components are getters to internals — the same
  leak, one level up.
- [`deep`](../deep/README.md): `AppPage.logout()` is a deep method (one name, a whole path behind it);
  `bad.AppPage` is a shallow module that only hands out other shallow modules.
- [`coupling`](../coupling/README.md): a train wreck is content coupling to every class on the path.
- Mocks: needing Mockito's `RETURNS_DEEP_STUBS` to unit-test something is a LoD alarm — the code under test
  walks a chain, so the test has to fake the whole chain.

## Pros / cons

| Pros | Cons |
|---|---|
| A UI restructure is a change in one page object, not in N tests | More methods on the page: one per user intention |
| Tests read as user actions; step order (open before click) is enforced inside | Risk of a god page: `AppPage` with 60 intentions → split by area (`UserMenuActions`, components with intentions) |
| Components can be refactored freely — nobody outside sees them | Wrapping data in forwarders when misapplied (see `OrderView`) |
| Fewer fakes/stubs to unit-test a caller | Some flexibility lost: a rare test that needs a component must ask the page to expose an intention for it |

## When NOT to apply
- **DTOs, records, JSON/DB rows** — navigate them freely; assert sub-structures with `usingRecursiveComparison`
  or `extracting`. A forwarder per field (`OrderView`) is a wider interface that hides nothing.
- **Fluent builders and DSLs** — `this`-returning chains and DSLs whose every step is its own type.
- **Inside one module** — a page object walking its own private components is the module's business.
- **Component tests** — a test *of* the `UserMenu` component talks to it directly; LoD is about callers
  reaching *through* something, not about who may be tested.
- **Counting dots** — `a.b().c()` where `b()` returns a value object you own (`Duration`, `URI`) is fine;
  a single dot `page.getDropdown()` that exposes internals is not.

## Try it yourself
1. Add `V3`: the user menu moves into a "More" overflow menu on narrow screens (two clicks to reach it).
   How many lines change in `bad/UserScenarios`? In `good`?
2. A test needs "the menu shows 3 items for a guest, 4 for an admin". Expose it without returning the
   `Dropdown`: what is the intention-level query? (`menuItems(): List<String>` — data out, behaviour in.)
3. `AppPage` grows to 40 intentions. Split it without bringing back train wrecks (hint: components with
   intentions — `page.userMenu().logout()` is one hop to a friend that hides the rest; is that a violation?).
4. Rewrite `OrderView` usages back to DTO navigation and AssertJ. What did you lose? What did you gain?
5. Smell check on your framework: grep tests for `.get[A-Z]\w*()\.get[A-Z]\w*()\.` on page objects, for
   `findElement(` outside page classes, and for `RETURNS_DEEP_STUBS`.
