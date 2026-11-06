# `record` and invariants — and where it stops protecting them

## What a record gives for free
- `private final` fields, no setters → state cannot change after creation;
- `final` class → no mutable subclass can be substituted;
- no extra instance fields → no hidden state outside the components;
- generated `equals` / `hashCode` / `toString` over all components.

## What you still have to do
- **Validate** in the compact constructor — immutability keeps a valid object valid, but does not stop
  you from creating an invalid one. See `DateRange`.
- **Copy mutable components** — `final` freezes the *reference*, not the object it points to. A record is
  fully immutable only if all its components are immutable. See `ShallowResult` (trap) vs `SafeResult` (fix).

| Class | What to look at |
|---|---|
| `DateRange` | validation in the compact constructor |
| `ShallowResult` | trap: record with a mutable `List` component |
| `SafeResult` | fix: defensive copy in the compact constructor |
| `RecordDemo` | runnable demonstration |
| `DateRangeTest`, `SafeResultTest` (tests) | the behaviour made visible |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.invariants.record.RecordDemo
mvn -q -pl principles test -Dtest='DateRangeTest,SafeResultTest'
```

## Where records fit in a test framework
- **Good fit:** test data and keys, configuration, value objects, DB rows, API DTOs (Jackson 2.12+).
- **Poor fit:** objects with a lifecycle and behaviour (browser session, DB connection, page objects),
  JPA entities, anything that needs class inheritance.

Back to [invariants](../README.md).
