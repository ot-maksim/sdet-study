# DIP — Dependency Inversion Principle

1. High-level modules must not depend on low-level modules; both depend on abstractions.
2. Abstractions must not depend on details; details depend on abstractions.

| Package | What to look at |
|---|---|
| `bad`  | `OrderTest` creates `JdbcUserCreator` itself → test is welded to JDBC |
| `good` | `OrderTest` gets `UserPrecondition` via constructor; `DipDemo` is the composition root |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.dip.bad.OrderTest
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.dip.good.DipDemo
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.dip.good.DipDemo -Dpreconditions=api
```

## Try it yourself
1. Add a `UiUserPrecondition` (registration via UI). How many files did you touch? (Answer for `good`: 1 new + 1 line in the root.)
2. Do the same in `bad`. Compare.
3. Rename the interface method to `insertRow(...)`. Why does this quietly break DIP even though everything compiles?
4. Smell check: grep the test layer for `java.sql`, `io.restassured`, `com.microsoft.playwright` imports.
