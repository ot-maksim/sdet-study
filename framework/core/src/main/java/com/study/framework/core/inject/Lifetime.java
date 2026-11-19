package com.study.framework.core.inject;

/** How long a bound object lives. A RUN object must never depend on a TEST one (captive dependency). */
public enum Lifetime {
    /** One per JUnit run, shared by all (also parallel) tests; must be thread-safe or immutable. Closed after the last test. */
    RUN,
    /** One per test method; closed after it, also when it failed. */
    TEST
}
