package com.study.principles.inheritance.session.good;

/**
 * GOOD: the base class OWNS the lifecycle state and guards its invariants.
 *
 * Invariants:
 *  1. Transitions go only NEW → STARTED → CLOSED.
 *  2. Actions are allowed only in STARTED ({@link #ensureStarted()}).
 *  3. close() is idempotent.
 *  4. If start() fails halfway, already acquired resources are released.
 *
 * How it is enforced:
 *  - state is private        → subclasses cannot corrupt it
 *  - public methods are final → subclasses cannot bypass the rules
 *  - subclasses only fill protected hooks: doStart() / doRelease()  (Template Method)
 */
public abstract class DriverSession implements AutoCloseable {

    public enum State { NEW, STARTED, CLOSED }

    private State state = State.NEW;

    public final void start() {
        if (state != State.NEW) {
            throw new IllegalStateException("Cannot start: session is " + state);
        }
        try {
            doStart();
            state = State.STARTED;
        } catch (RuntimeException e) {
            state = State.CLOSED;
            safeRelease();                 // invariant 4
            throw e;
        }
    }

    @Override
    public final void close() {
        if (state == State.CLOSED) return; // invariant 3
        state = State.CLOSED;
        safeRelease();
    }

    public final State state() {
        return state;
    }

    /** Invariant 2 — available to subclasses, but they don't have to re-implement it. */
    protected final void ensureStarted() {
        if (state != State.STARTED) {
            throw new IllegalStateException("Session is " + state + ", expected STARTED");
        }
    }

    // --- extension points: "what to do", never "when it is allowed" ---

    protected abstract void doStart();

    /** Must tolerate partially initialized state: may be called after doStart() failed halfway. */
    protected abstract void doRelease();

    private void safeRelease() {
        try {
            doRelease();
        } catch (RuntimeException e) {
            System.out.println("  [warn] release failed: " + e.getMessage()); // in a framework: log, don't mask the test result
        }
    }
}
