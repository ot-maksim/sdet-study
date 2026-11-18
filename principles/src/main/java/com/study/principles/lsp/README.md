# LSP — Liskov Substitution Principle

**A subtype must be usable wherever its base type is expected, without the caller noticing.** Not "has the
same methods" — the compiler checks that — but "keeps the same *promises*": the caller wrote code against the
base type's contract, and every implementation must honour it.

| Rule | Subtype must not… | Automation example |
|---|---|---|
| Preconditions | demand more from the caller | `ReadOnlyDbClient.deleteUsers` — "never call me" |
| Postconditions | promise less | `SilentReadOnlyDbClient.deleteUsers` returns, data stays |
| Invariants | break what the base keeps true | a fake that stores two users with one e-mail |
| Exceptions | throw new kinds of exceptions | `UnsupportedOperationException` from a write |
| History | allow state changes the base forbids | a fake whose returned objects mutate storage |

Two parts:

| Package | What to look at |
|---|---|
| `db/bad` | `ReadOnlyDbClient extends DbClient` throws on writes (loud); `SilentReadOnlyDbClient` makes them no-ops (silent); `TestDataCleaner(DbClient)` in `@AfterEach`; `patched/TestDataCleaner` — the `instanceof` "fix" |
| `db/good` | `DbReader` / `DbWriter extends DbReader`; `ReplicaClient implements DbReader`, `PrimaryClient implements DbWriter`; `TestDataCleaner(DbWriter)`, `UserChecks(DbReader)` |
| `fakes` | `UserRepository` with a written contract; `JdbcLikeUserRepository` (stand-in for the real one: rows → copies, unique index); code under test `RegistrationService`, `ProfileService` (with a bug: forgets `update`) |
| `fakes/bad` | `LenientFakeUserRepository`: no unique check, hands out stored instances |
| `fakes/good` | `InMemoryUserRepository`: unique e-mail, copies in and out |
| test sources | `DbClientContract`, `DbReaderContract`, `DbWriterContract`, `UserRepositoryContract` — contracts as JUnit test interfaces |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.lsp.LspDemo
mvn -q -pl principles test -Dtest='BadDbClientTest,GoodDbClientsTest,UserRepositoryContractTest,FakeDriftTest'
```

- `BadDbClientTest` — the base `DbClient` passes its own contract; the same contract through EngineTestKit
  fails twice for each read-only subclass — with `UnsupportedOperationException` (loud) or `AssertionError`
  (silent: no exception, wrong state). In the framework: cleanup throws *after* the test passed; the silent
  client reports success and leaves the data; the `instanceof` patch stops the exception, not the problem.
- `GoodDbClientsTest` — the replica passes the reader contract; the primary passes the writer contract, which
  extends the reader's; `TestDataCleaner(new ReplicaClient(..))` does not compile.
- `UserRepositoryContractTest` — the real repository and the good fake pass the same contract; the lenient fake
  fails exactly uniqueness and copy semantics.
- `FakeDriftTest` — the price: on the lenient fake a duplicate registration "succeeds" and the missing-`update`
  bug is green; on the real repository and the good fake both are visible.

## Contract tests: the LSP check you can run
A contract written as a JUnit **test interface** (`interface XContract { X create(); @Test default void … }`)
is implemented once per implementation. A new implementation gets every clause by writing one method; a new
clause covers every implementation at once. For fakes this is the difference between a test double and a
liability: **a fake you do not run the contract against will drift from the real thing** — and every test
written against it inherits the drift. In stage 2 `UserRepositoryContract` runs against JDBC on Postgres.

Same idea elsewhere in the repo: `inheritance/session`'s `SessionInvariantsTest` (one lifecycle contract for
all sessions), `ocp`'s parameterized test over all browser profiles.

## Signals of an LSP violation
- `throw new UnsupportedOperationException()` / empty overrides in a subclass.
- `instanceof` / `getClass()` checks in callers: "if it is the read-only one, skip".
- Javadoc on a subclass saying "do not call X".
- A test green on the fake, red on the environment.
- The refused bequest in [`inheritance/pages`](../inheritance/pages/README.md): `SettingsPage.navigateTo()`.

## Pros / cons of designing for substitutability

| Pros | Cons |
|---|---|
| Callers rely on the type, not on knowing implementations | Hierarchies get split by capability: more interfaces (see ISP) |
| Wrong wiring is a compile error (`TestDataCleaner(DbWriter)`) | Contracts must be written down, and kept with the code |
| Fakes become trustworthy, contract tests catch drift | Contract tests for every fake cost time to write |

## When NOT to worry
- **Documented optional behaviour** — `List.of(..).add` throws `UnsupportedOperationException`, and that is in
  `List`'s contract ("optional operation"). The contract is what is documented; a caller that ignores it is
  the bug. Prefer not to design your own APIs this way, though.
- **Implementations that differ in what the contract leaves open** — ordering when it is unspecified, timing,
  log format. Contract tests should check only the promises, not one implementation's details.
- **A throwaway stub in one test** — `users -> Optional.empty()` for one scenario is fine; LSP matters for fakes
  that many tests share.
- **Sealed hierarchies matched exhaustively** — when every caller switches on the concrete type by design
  (`sealed interface Result permits Ok, Failed`), substitution is not the point.

## Try it yourself
1. Make `ReadOnlyDbClient.deleteUsers` log a warning instead of throwing. Which contract test fails now — and
   what kind of violation did you turn the loud one into?
2. Fix `ProfileService` (call `users.update(user)`) and watch which `FakeDriftTest` tests turn red. Why are the
   tests on the real repository red after a *correct* fix? (They pinned the bug — characterization.) Which one
   stays the same, and what does that say about the lenient fake?
3. Fix `LenientFakeUserRepository` one clause at a time, running `UserRepositoryContractTest` after each.
4. Add a `findAll()` to `UserRepository` with "ordered by id" in the contract. Which implementation breaks the
   new clause first?
5. Write `CachingUserRepository` (a decorator with a read cache). What must it do on `update` to stay
   substitutable? Write the contract test class for it — one method.
6. Smell check on your framework: grep for `UnsupportedOperationException`, `instanceof .*Client`, and fakes
   without a contract test.
