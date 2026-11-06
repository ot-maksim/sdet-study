package com.study.principles.invariants.testrun.bad;

import com.study.principles.invariants.testrun.Status;
import com.study.principles.invariants.testrun.TestResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * BAD: a test run modelled as a bag of data with a setter for every field (CRUD over fields).
 *
 * <h2>Intended invariants</h2>
 * <ol>
 *   <li>{@code status == FINISHED} &lt;=&gt; {@code finishedAt != null}</li>
 *   <li>{@code finishedAt >= startedAt}</li>
 *   <li>{@code passed + failed == results.size()}</li>
 *   <li>no results after {@code FINISHED}</li>
 * </ol>
 *
 * <h2>Problems — the class guarantees none of them</h2>
 * <table border="1">
 *   <caption>Ways to break the invariants</caption>
 *   <tr><th>Action</th><th>Broken invariant</th></tr>
 *   <tr><td>{@code setStatus(FINISHED)} without {@code setFinishedAt}</td><td>1</td></tr>
 *   <tr><td>{@code setFinishedAt(..)} without changing the status</td><td>1</td></tr>
 *   <tr><td>{@code setFinishedAt} earlier than {@code setStartedAt}</td><td>2</td></tr>
 *   <tr><td>{@code getResults().add(..)} without updating a counter</td><td>3</td></tr>
 *   <tr><td>{@code setPassed(100)} directly</td><td>3</td></tr>
 *   <tr><td>{@code setResults(otherList)} / {@code setResults(null)}</td><td>3 (+ NPE later)</td></tr>
 *   <tr><td>{@code getResults().add(..)} after {@code FINISHED}</td><td>4</td></tr>
 *   <tr><td>{@code setStatus(RUNNING)} after {@code FINISHED} ("resurrection")</td><td>1, 4</td></tr>
 *   <tr><td>a caller keeps the list reference and modifies it later</td><td>3, 4</td></tr>
 * </table>
 *
 * <ul>
 *   <li><b>Rules live in the callers.</b> Every listener, reporter and retry mechanism must call the
 *       setters "correctly" and in the right order. The knowledge is duplicated and nobody owns it.</li>
 *   <li><b>Shallow interface.</b> The class does almost no work; the complexity of orchestrating it
 *       is pushed to every client.</li>
 *   <li><b>Stored derived values</b> ({@code passed}, {@code failed}) must be synchronised by hand.</li>
 *   <li><b>Hidden dependency on the system clock</b> in callers ({@code Instant.now()}) makes timing
 *       non-deterministic in tests.</li>
 *   <li><b>Untestable invariants.</b> There is nothing to test — the class forbids nothing. Covering it
 *       would mean enumerating orders of seven setter calls.</li>
 * </ul>
 *
 * @see com.study.principles.invariants.testrun.good.TestRun the fixed version
 */
public class TestRun {
    private Status status = Status.CREATED;
    private Instant startedAt;
    private Instant finishedAt;
    private List<TestResult> results = new ArrayList<>();
    private int passed;
    private int failed;

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    /** Returns the live internal list — callers can modify it freely. */
    public List<TestResult> getResults() { return results; }
    public void setResults(List<TestResult> results) { this.results = results; }

    public int getPassed() { return passed; }
    public void setPassed(int passed) { this.passed = passed; }

    public int getFailed() { return failed; }
    public void setFailed(int failed) { this.failed = failed; }
}
