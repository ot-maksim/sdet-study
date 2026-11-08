# Class invariants

What they are, how they get broken and how an object protects them.

## Definition

An **invariant** (Latin *in-* "not" + *varians* "changing") is a condition that is true for an object
during its whole lifetime: right after construction and after every public operation. It is a *rule*
describing a valid state; a state that violates the rule is an invalid (corrupted) state.

Examples:
- `from <= to` for a date range;
- `passed + failed == total` for a test suite result;
- `status == FINISHED <=> finishedAt != null` for a test run.

## Part of the class contract (Design by Contract, B. Meyer)

| Element | Meaning | Example |
|---|---|---|
| **Precondition** | what must be true *before* a method is called | argument is not null |
| **Postcondition** | what the method guarantees *after* it returns | `create()` returns a user with an id |
| **Invariant** | what is true for the object *always*, between any calls | `from <= to` |

An invariant may be temporarily broken *inside* a method, but must hold again at the boundaries:
after the constructor and after each public method, i.e. whenever outside code can observe the object.

## Who is responsible

The object itself is the guardian of its invariants. It must:
1. refuse to be created in an invalid state (validate in the constructor, fail fast);
2. not let outside code change its state bypassing its methods
   (no leaked references to mutable internals, no "setter for every field");
3. not break the rules in its own methods.

> `private` hides the *field*, it does not make the *object the field points to* immutable.
> Encapsulation is more than "private fields + getters".

## Examples in this package

| Package | Topic |
|---|---|
| [`leak`](leak/README.md) | a mutable collection leaks through a getter |
| [`hashkey`](hashkey/README.md) | a mutable object used as a `HashMap` key |
| [`record`](record/README.md) | `record`: compact constructor and the "shallow immutability" trap |
| [`testrun`](testrun/README.md) | setters for every field vs domain operations; why a well encapsulated object is easy to test |
| [`retry`](retry/README.md) | immutable value object: invariants checked once in the constructor, trusted by every client |

Each sub-package has a `*Demo` class with `main()` and matching unit tests under `src/test/java`.

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.invariants.leak.LeakDemo
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.invariants.hashkey.HashKeyDemo
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.invariants.record.RecordDemo
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.invariants.testrun.TestRunDemo
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.invariants.retry.RetryDemo
mvn -q -pl principles test -Dtest='com.study.principles.invariants.**.*Test'
```
