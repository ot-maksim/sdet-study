# Inheritance vs composition

**Inheritance** (IS-A) shares a *contract and its invariants*: the subclass is a substitutable kind of the
parent. **Composition** (HAS-A) shares *behaviour*: an object is assembled from parts that vary
independently. In automation, inheritance is mostly used for the wrong reason — "reuse code that every test
or page needs" — and that gives a `BaseTest` god class and page trees that cannot fit the screen.

| Sub-kata | Inheritance used… | What to look at |
|---|---|---|
| [`session`](session/README.md) | **right**: one shallow level, the base class owns state with an invariant (Template Method) | abstract `DriverSession` vs `final ManagedSession` + injected driver |
| [`basetest`](basetest/README.md) | **wrong**: test fixtures by inheritance | `BaseTest` god class, split hierarchy `UiDbBaseTest` vs JUnit 5 extensions + meta-annotations |
| [`pages`](pages/README.md) | **wrong**: widgets by inheritance | `PageWithSidebarAndSearch` tree, `Mobile*Page` per leaf vs components + one device decision |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.inheritance.session.SessionDemo
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.inheritance.pages.PagesDemo
mvn -q -pl principles test -Dtest='BadSessionTest,SessionInvariantsTest,BaseTestVsExtensionsTest,PageHierarchyVsComponentsTest'
```

## Is it IS-A? A checklist
Inheritance is fine only if **all** hold:
1. **Substitutable** — every place that takes the parent works with the child, without `instanceof` or
   "don't call that on this one" (LSP). `SettingsPage.navigateTo()` fails this.
2. **One axis of variation** — subclasses differ in one thing. Page widgets × device are two axes →
   the tree multiplies (`MobileSettingsPage`, `MobileReportsPage`, …).
3. **The parent protects its state** — `private` fields, `final` public methods, `protected abstract` hooks.
   A parent of `protected` fields and overridable methods is a *fragile base class*: subclasses can break it
   (`BaseTest.setUp()` shadowed) and it can break subclasses (a new field in `setUp()` changes every test).
4. **Shallow** — one level, two at most. Each level is something to read before you understand a leaf.
5. **Not for convenience** — "every test needs `retry()`" is a static helper; "every page needs a header"
   is a component.

## Symptoms of inheritance for reuse

| Symptom | Example | Composition answer |
|---|---|---|
| Name lists features | `PageWithSidebarAndSearch`, `UiDbBaseTest` | components / one extension per feature |
| Copy between siblings | `MobileDashboardPage` and `MobileSettingsPage` override the same two methods | inject the varying part (`Header`) |
| Method that throws "not supported" or fails here | `SettingsPage.navigateTo()` | don't expose what the object does not have |
| Paying for unused parents | API test launches a browser | resolve only requested parameters |
| `super.setUp()` discipline, order of `@BeforeEach` up the tree | `BaseTest` subclasses | independent extensions, explicit in the test signature |
| Protected fields used everywhere | `browser`, `user`, `db` appear from nowhere | parameters: the signature tells what the test needs |

## Pros / cons

| | Inheritance | Composition |
|---|---|---|
| Reuse of code | free, automatic | one field / one annotation per part |
| Varying several things | combinatorial classes | combine parts |
| Coupling | subclass knows parent internals (`protected`) | parts know only interfaces |
| Reading a leaf class | read the whole chain | read the class + names of its parts |
| Enforcing an invariant for all variants | natural (`final` template method) | needs one owner (`ManagedSession`) |
| Decorating (logging, retry, screenshots) | another subclass level | wrap the part |

## When NOT to replace inheritance
- **A real IS-A with a protected invariant** — `session`'s `DriverSession`: one level, `final` lifecycle,
  two hooks. Composition would add an object and gain little.
- **Framework-required base types** — `TestNG`'s `IHookable`, Playwright's or Cucumber's extension points,
  `RuntimeException` subclasses for your errors, `AbstractMap`-style skeletons. Extend what the library
  designed for extension.
- **One tiny `BaseTest` with nothing but annotations** — `@UiTest` on an abstract class is harmless; the problem
  is state and helpers, not the `extends` keyword.
- **Sealed hierarchies of data** — `sealed interface Result permits Ok, Failed`: closed, exhaustive, no shared
  mutable state. That is algebraic data, not reuse.

## Try it yourself
See each sub-kata's README.
