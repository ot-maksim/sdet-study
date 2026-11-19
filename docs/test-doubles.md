# Test doubles: dummy, stub, spy, mock, fake

A reference note. Vocabulary after Gerard Meszaros (*xUnit Test Patterns*); Mockito blurs it, because one
`mock()` object becomes a stub with `when(...)` and a mock with `verify(...)`. The repo uses no Mockito: the
examples below are hand-written doubles that already exist here.

## The five kinds

| Double | What it does | The test checks | Example in this repo |
|---|---|---|---|
| **Dummy** | nothing; fills a parameter that the path under test never uses | — | `Map.of()` for consumer properties a test does not care about |
| **Stub** | returns canned answers to queries | the result of the code under test | a fixed `Clock` (`Clock.fixed(...)`, [`principles/time`](../principles/src/main/java/com/study/principles/time/README.md)) |
| **Spy** | a stub that also records the calls it got | what was recorded, after the fact | `published::add` into a list in `sut/.../UserServiceEventsTest` — the events port as a recorder |
| **Mock** | is told in advance which calls must happen, and verifies them | the interaction itself (`verify(x).send(...)`) | none here; with Mockito: `verify(mailer).send(ada)` |
| **Fake** | a working, simplified implementation | the resulting state | `InMemoryUserRepository` (SUT), `FakeBrowser` ([`principles/coupling`](../principles/src/main/java/com/study/principles/coupling/README.md)), `FakeBroker` (`principles/concurrency/events/kafka`) |

## Fake vs mock: state vs interaction

```java
// Fake: real behaviour; the test asks "what is the result?"
var users = new InMemoryUserRepository();
var service = new UserService(users, clock, events);
service.create(ada);
assertThat(users.findByEmail("ada@example.com")).isPresent();
assertThatThrownBy(() -> service.create(ada)).isInstanceOf(UserRejectedException.class);   // the fake remembers ada

// Mock: no behaviour until told; the test asks "was the right method called?"
var users = mock(UserRepository.class);
when(users.findByEmail("ada@example.com")).thenReturn(Optional.empty());
new UserService(users, clock, events).create(ada);
verify(users).insert(any(), any());
```

| | Fake | Mock |
|---|---|---|
| Refactoring the code under test | survives: dropping the `findByEmail` pre-check and relying on `insert` rejecting a taken email changes nothing | breaks: it pinned the exact call |
| Cost | an implementation to write and keep correct | one line per expectation |
| Main risk | drifts from the real thing (more lenient than production) | repeats the implementation ("checked that the code does what the code says") |
| Reuse | one fake for hundreds of tests | `when(...)` again in every test |
| Narrow interface (2–5 methods) | easy | possible |
| Wide interface (`JpaRepository`, ~30 methods) | impractical | the only sensible choice |

## Which one to use

- **Queries** (the code asks something: "does the user exist?", "what time is it?") → **stub or fake**, assert the
  result. A `verify` on a query checks how the code is written, not what it does.
- **Commands to the outside world** (the call *is* the outcome: "email sent", "event published", "retried
  exactly 3 times") → **spy or mock**: there is nothing else to observe.
- **Dependencies with state** (storage, broker, browser) → **fake**, if the interface is narrow enough to
  implement.
- **Parameters the path does not touch** → **dummy**.

## Keeping a fake honest

A fake is a second implementation of a port, so it obeys the same contract (LSP for fakes). Run the fake and the
real adapter through **one contract test**:

| Contract | Implementations |
|---|---|
| `sut/.../UserRepositoryContract` | `InMemoryUserRepository`, `JdbcUserRepository` |
| `framework/domain/.../UserPreconditionContract` | `ApiUserPrecondition`, `DbUserPrecondition` |

A fake without email uniqueness keeps tests green while production rejects the same data — see the
"Fake that is more lenient than the real thing" row in [`principles/SMELLS.md`](../principles/SMELLS.md).

## Where a double does not belong

- **Replacing the database engine** (H2 instead of Postgres) is not a double of a port: the code still speaks SQL,
  and the dialect differs. The adapter is tested against the real database (Testcontainers, ADR 0009); the core
  is tested against a fake of the port.
- **The SUT itself in client tests**: when the real service starts in milliseconds, a hand-written mock server
  only adds drift (ADR 0003: the API client runs against the SUT in-process).
- **Deep stubs** (`RETURNS_DEEP_STUBS`, `when(a.getB().getC())`) — a Law of Demeter smell, not a test technique
  ([`principles/demeter`](../principles/src/main/java/com/study/principles/demeter/README.md)).

## The architecture behind it

A double is only possible where there is a seam — an interface the code under test depends on. If the business
rules can be checked without any external technology (not even an in-memory database), the dependencies point
inward (ports and adapters). If some database is needed to check a rule that has nothing to do with storage,
the rule lives in SQL or the core talks JDBC directly: only an integration test can cover it.
