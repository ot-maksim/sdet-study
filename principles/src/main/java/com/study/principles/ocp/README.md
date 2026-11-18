# OCP — Open/Closed Principle

**Open for extension, closed for modification:** adding a variant (a browser, an environment, a channel)
should be *new* code, not edits to code that already works. The smell is not `switch` itself but the
**same switch repeated** in several places: each new value is an edit in each of them, and the one you miss
is not a compile error — it falls into `default`.

| Package | What to look at |
|---|---|
| `bad` | three switches on the browser name (`DriverFactory`, `TimeoutSettings`, `ViewportSettings`) in the state "remote was added to one of them"; `EnvConfig` — environments as switches, safety check written as `!prod` |
| `good` | `BrowserProfile` — Strategy (name, timeouts, viewport, launch); four built-in profiles; `BrowserProfiles` — registry with fail-fast on unknown/duplicate names and `ServiceLoader` discovery; `Environment` — environments as **data** (`resources/ocp/env/*.properties`), destructive tests opt-in |
| root | `Browser`, `Timeouts`, `Viewport` records; `OcpDemo` |
| test sources | `RemoteGridProfile` + `META-INF/services/...BrowserProfile` — a profile added without editing `main`; `ocp/env/preprod.properties` — an environment added as a file |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.ocp.OcpDemo
mvn -q -pl principles test -Dtest='OcpTest'
```

- **bad** (characterization): `remote` launches on the grid but silently gets local Chrome timeouts; asking its
  viewport throws at run time; `preprod` is open to destructive tests the moment its URLs are added. In the
  demo `edge` cannot launch, yet `TimeoutSettings` happily returns values for it.
- **good**: one parameterized contract test runs over *every* discovered profile — `remote` from the test
  sources is checked without anyone adding it to the test; unknown names list what exists; `preprod` is a file
  and is protected by default.

## Two kinds of variation, two answers

| Variants differ in… | Answer | Here | New variant costs |
|---|---|---|---|
| **behaviour** (how to launch, wait, authenticate) | Strategy + one place that picks it (registry / root) | `BrowserProfile` | one class (+ one line or a services entry) |
| **values** only (URLs, credentials, flags) | configuration data | `Environment` + `.properties` | one file, no code |
| a **closed, small** set, owned by one module | `enum` with behaviour, or one `switch` | see below | one constant / one branch, compiler-checked |

Making environments a class hierarchy (`QaEnvironment extends Environment`) is the common over-design:
behaviour does not differ, so there is nothing for polymorphism to do.

## When a switch is fine
- **One switch, at the edge.** Turning a string from `-Dbrowser` into an object has to happen somewhere; one
  `switch` in the composition root (see [`coupling`](../coupling/README.md)) is a registry written by hand. The
  problem is the second and third copy.
- **Exhaustive by the compiler.** `switch` over an `enum` without `default` (Java 17: `switch` expression over
  an enum fails to compile if a constant is missing — when no `default` is there) or over a `sealed` type (pattern
  matching, Java 21). Adding a constant then breaks the build at every switch — loud, not silent.
- **Enum with behaviour** — a middle ground for a closed set:
  ```java
  enum Browsers {
      CHROME(Duration.ofSeconds(15), Viewport.DESKTOP),
      MOBILE_WEB(Duration.ofSeconds(25), Viewport.PHONE);
      ...
  }
  ```
  All data for a variant in one line; no registry; but closed — another module cannot add a constant.

## Pros / cons

| Pros | Cons |
|---|---|
| New variant = new class/file; existing, tested code untouched | Indirection: "where is Chrome configured?" — find the profile, not a switch |
| No forgotten branch: the interface forces every answer | A wrong abstraction is hard to change: every implementation follows its shape |
| Contract tests cover future variants for free | `ServiceLoader` / plugins add magic: what is on the class path decides behaviour |
| Unknown names fail at startup with the list of options | Registry + strategy for two stable variants is ceremony |

## When NOT to apply
- **Two variants that have not changed in years** — `if (mobile)` in one place is fine. Rule of three:
  abstract when the third variant arrives, or when the second switch on the same value appears.
- **Speculative extension points** — `BeforeClickHook`, `ClickStrategy`, plugin interfaces with one
  implementation "for flexibility". Each is code to read and keep correct; YAGNI ([`kiss`](../kiss/README.md)).
- **Plugins inside one repository** — `ServiceLoader` earns its keep across modules or teams. In a single
  module a list in the registry is simpler and greppable; the kata uses it to show the mechanism.
- **Values pretending to be behaviour** — environments, users, locales: put them in data.

## Try it yourself
1. Add `edge` to `bad`. How many files? Then forget one of them on purpose — what fails, and when?
2. Add `edge` to `good` as a built-in. Then add `tablet` as a plugin in the test sources only. Which tests
   cover them without being edited?
3. Turn `BrowserProfiles` into an `enum Browsers` with fields. What became simpler, what can no longer be done?
4. Add `preprod` in `bad` with the right safety: rewrite `destructiveAllowed` so a new environment is safe by
   default. Notice it is the same move as in `good`: opt-in instead of opt-out.
5. Stage 4 preview: an `EnvironmentProvider` with "already deployed (URL from config)" and "Testcontainers
   (start Postgres + SUT)" implementations. Is that Strategy or data? (Hint: behaviour differs.)
6. Smell check on your framework: grep for `switch (browser` / `equals("chrome")` / `env.equals(` and count the
   files; look for `default ->` branches that return a value instead of throwing.
