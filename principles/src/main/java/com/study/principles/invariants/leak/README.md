# Invariant violation #1: a mutable collection leaks through a getter

The field is `private` and there is no setter, so the class *looks* protected.
But the getter returns a reference to the internal list, and outside code changes the state
bypassing the object's methods.

| Class | What to look at |
|---|---|
| `bad.TestSuiteResult` | the leak |
| `good.TestSuiteResult` | defensive copy + derived value instead of a stored counter |
| `LeakDemo` | runnable demonstration |
| `TestSuiteResultTest` (test) | the difference made visible |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.invariants.leak.LeakDemo
mvn -q -pl principles test -Dtest=TestSuiteResultTest
```

Back to [invariants](../README.md).
