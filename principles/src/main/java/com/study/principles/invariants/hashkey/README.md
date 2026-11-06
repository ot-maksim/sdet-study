# Invariant violation #2: a mutable object is changed while another object relies on it

`HashMap` has an invariant: an entry is stored in the bucket that matches its key's `hashCode()`.
If the key is mutated after insertion and the mutated field takes part in `hashCode()`, the entry stays
in the "old" bucket while lookups go to the "new" one — the entry becomes unreachable.
The map did nothing wrong: its invariant was broken by changing an object it depends on.

| Class | What to look at |
|---|---|
| `bad.TestUser` | mutable key |
| `good.TestUser` | immutable `record` key |
| `HashKeyDemo` | runnable demonstration |
| `TestUserTest` (test) | the difference made visible |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.invariants.hashkey.HashKeyDemo
mvn -q -pl principles test -Dtest=TestUserTest
```

Back to [invariants](../README.md).
