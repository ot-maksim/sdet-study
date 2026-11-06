package com.study.principles.invariants.testrun.good;

import com.study.principles.invariants.testrun.Status;
import com.study.principles.invariants.testrun.TestResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * GOOD: a test run whose state changes only through domain operations forming a state machine.
 *
 * <pre>
 *   CREATED --start()--&gt; RUNNING --finish()--&gt; FINISHED
 *                          |  ^
 *                          +--+ record(result)
 * </pre>
 *
 * <h2>Invariants (guaranteed by the class)</h2>
 * <ol>
 *   <li>{@code status() == FINISHED} &lt;=&gt; {@link #duration()} is present;</li>
 *   <li>{@code finishedAt >= startedAt};</li>
 *   <li>{@code passed() + failed() == results().size()};</li>
 *   <li>no results after {@code FINISHED}.</li>
 * </ol>
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li><b>Operations instead of setters.</b> Only {@link #start()}, {@link #record(TestResult)} and
 *       {@link #finish()} change the state. Each one is a transition that checks the current state
 *       first, so illegal sequences fail with {@link IllegalStateException} right where they happen.</li>
 *   <li><b>Related fields change together.</b> {@link #finish()} sets the status and the finish time in
 *       one method — they can never be observed out of sync (invariant 1).</li>
 *   <li><b>Time is taken inside, in order.</b> {@code startedAt} and {@code finishedAt} come from the
 *       clock during {@code start()}/{@code finish()}, so callers cannot pass them in the wrong order
 *       (invariant 2).</li>
 *   <li><b>Derive, don't store.</b> {@link #passed()} and {@link #failed()} are computed from the
 *       results, so invariant 3 holds by construction.</li>
 *   <li><b>No leaks.</b> The list field is {@code final} and {@link #results()} returns an unmodifiable
 *       copy (invariants 3 and 4).</li>
 *   <li><b>Injected {@link Clock} (Dependency Injection).</b> Time is a volatile dependency; injecting it
 *       makes tests deterministic ({@code Clock.fixed(..)}) instead of depending on
 *       {@code Instant.now()}.</li>
 *   <li><b>Deep interface.</b> Clients express intent ({@code start / record / finish}) instead of
 *       orchestrating seven setters.</li>
 * </ul>
 * Testability follows from the design: the unit tests are simply the list of allowed and forbidden
 * transitions (see {@code GoodTestRunTest}).
 *
 * @see com.study.principles.invariants.testrun.bad.TestRun the broken version
 */
public final class TestRun {
    private final Clock clock;
    private final List<TestResult> results = new ArrayList<>();
    private Status status = Status.CREATED;
    private Instant startedAt;
    private Instant finishedAt;

    /**
     * @param clock source of time; use {@link Clock#systemUTC()} in production and a fixed clock in tests
     */
    public TestRun(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Starts the run. Transition {@code CREATED -> RUNNING}.
     *
     * @throws IllegalStateException if the run is not in {@code CREATED}
     */
    public void start() {
        requireStatus(Status.CREATED);
        startedAt = clock.instant();
        status = Status.RUNNING;
    }

    /**
     * Records the result of one test. Allowed only while {@code RUNNING}.
     *
     * @param result test result, not {@code null}
     * @throws IllegalStateException if the run is not in {@code RUNNING}
     * @throws NullPointerException  if {@code result} is {@code null}
     */
    public void record(TestResult result) {
        requireStatus(Status.RUNNING);
        results.add(Objects.requireNonNull(result, "result"));
    }

    /**
     * Finishes the run. Transition {@code RUNNING -> FINISHED}; sets status and finish time together.
     *
     * @throws IllegalStateException if the run is not in {@code RUNNING}
     */
    public void finish() {
        requireStatus(Status.RUNNING);
        finishedAt = clock.instant();
        status = Status.FINISHED;
    }

    public Status status() {
        return status;
    }

    /** Number of passed tests; computed, never stored. */
    public long passed() {
        return results.stream().filter(TestResult::passed).count();
    }

    /** Number of failed tests; computed, never stored. */
    public long failed() {
        return results.size() - passed();
    }

    /** Read-only snapshot of recorded results. */
    public List<TestResult> results() {
        return List.copyOf(results);
    }

    /** Duration of the run; present if and only if the run is {@code FINISHED}. */
    public Optional<Duration> duration() {
        return finishedAt == null ? Optional.empty() : Optional.of(Duration.between(startedAt, finishedAt));
    }

    private void requireStatus(Status expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected " + expected + " but was " + status);
        }
    }
}
