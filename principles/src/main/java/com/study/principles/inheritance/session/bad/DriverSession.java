package com.study.principles.inheritance.session.bad;

import com.study.principles.inheritance.session.FakePage;

/**
 * BAD: the base class is just a "shelf" for shared fields.
 * State is protected and every method is overridable, so each subclass
 * re-implements the lifecycle rules — and each one gets them wrong in its own way.
 */
public abstract class DriverSession implements AutoCloseable {
    protected boolean started;   // any subclass may set these however it likes
    protected boolean closed;

    public abstract void start();

    public abstract FakePage page();

    @Override
    public abstract void close();

    public boolean isClosed() {
        return closed;
    }
}
