# "A setter for every field" vs domain operations — and why the second is easy to test

## Invariants of a test run
1. `status == FINISHED` ⇔ `finishedAt != null`;
2. `finishedAt >= startedAt`;
3. `passed + failed == results.size()`;
4. no results can be added after `FINISHED`.

## The architectural signal
If an invariant is hard to test because the object can be broken in a dozen ways, that is a design
problem, not a testing problem. A well encapsulated object changes its state through a small number of
domain operations, and checking its invariants is a short list of allowed and forbidden transitions.

| Class | What to look at |
|---|---|
| `bad.TestRun` | 7 setters + a leaked list: 10+ ways to break, nothing to test |
| `good.TestRun` | 3 operations (`start / record / finish`) forming a state machine; 6 tests cover every transition |
| `Status`, `TestResult` | shared types |
| `TestRunDemo` | runnable demonstration |
| `GoodTestRunTest`, `TestRunPropertyTest` (tests) | transitions and properties |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.invariants.testrun.TestRunDemo
mvn -q -pl principles test -Dtest='GoodTestRunTest,TestRunPropertyTest'
```

## Review rule of thumb
If a class has a setter for every field, ask "what operations are actually performed on it?"
There are usually 2–4, and those should be its public methods.

Back to [invariants](../README.md).
