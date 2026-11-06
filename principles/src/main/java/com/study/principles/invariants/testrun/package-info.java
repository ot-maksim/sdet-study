/**
 * "A setter for every field" vs domain operations — and why the second is easy to test.
 *
 * <h2>Invariants of a test run</h2>
 * <ol>
 *   <li>{@code status == FINISHED} &lt;=&gt; {@code finishedAt != null};</li>
 *   <li>{@code finishedAt >= startedAt};</li>
 *   <li>{@code passed + failed == results.size()};</li>
 *   <li>no results can be added after {@code FINISHED}.</li>
 * </ol>
 *
 * <h2>The architectural signal</h2>
 * If an invariant is hard to test because the object can be broken in a dozen ways, that is a design
 * problem, not a testing problem. A well encapsulated object changes its state through a small number
 * of domain operations, and checking its invariants is a short list of allowed and forbidden
 * transitions.
 * <ul>
 *   <li>{@link com.study.principles.invariants.testrun.bad.TestRun} — 7 setters + a leaked list:
 *       10+ ways to break, nothing to test.</li>
 *   <li>{@link com.study.principles.invariants.testrun.good.TestRun} — 3 operations
 *       ({@code start / record / finish}) forming a state machine; 6 tests cover every transition.</li>
 * </ul>
 * Review rule of thumb: if a class has a setter for every field, ask "what operations are actually
 * performed on it?" There are usually 2–4, and those should be its public methods.
 */
package com.study.principles.invariants.testrun;
