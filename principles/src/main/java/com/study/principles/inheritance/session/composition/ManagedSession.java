package com.study.principles.inheritance.session.composition;

import com.study.principles.inheritance.session.FakePage;

/**
 * Same invariants as good.DriverSession, but enforced by composition:
 * the class is final, the driver is injected through the constructor (DI).
 *
 * + no inheritance coupling, drivers are trivially replaceable with fakes/decorators (e.g. a logging driver)
 * − one more object and one more indirection compared to a single abstract class
 */
public final class ManagedSession implements AutoCloseable {

    public enum State { NEW, STARTED, CLOSED }

    private final SessionDriver driver;
    private State state = State.NEW;
    private FakePage page;

    public ManagedSession(SessionDriver driver) {
        this.driver = driver;
    }

    public void start() {
        if (state != State.NEW) {
            throw new IllegalStateException("Cannot start: session is " + state);
        }
        try {
            page = driver.acquire();
            state = State.STARTED;
        } catch (RuntimeException e) {
            state = State.CLOSED;
            safeRelease();
            throw e;
        }
    }

    public FakePage page() {
        if (state != State.STARTED) {
            throw new IllegalStateException("Session is " + state + ", expected STARTED");
        }
        return page;
    }

    @Override
    public void close() {
        if (state == State.CLOSED) return;
        state = State.CLOSED;
        safeRelease();
    }

    public State state() {
        return state;
    }

    private void safeRelease() {
        try {
            driver.release();
        } catch (RuntimeException e) {
            System.out.println("  [warn] release failed: " + e.getMessage());
        }
    }
}
