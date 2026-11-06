package com.study.principles.inheritance.session.composition;

import com.study.principles.inheritance.session.FakePage;

/**
 * Alternative without inheritance: "how to acquire/release" is a pluggable part (HAS-A),
 * the lifecycle rules live in one final class, {@link ManagedSession}.
 */
public interface SessionDriver {
    FakePage acquire();

    /** Must tolerate partially initialized state. */
    void release();
}
