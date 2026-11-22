package com.study.project.environment;

import java.util.Optional;

/**
 * "Where does the environment come from": a stand somebody else runs, or one this run starts.
 * Two implementations, chosen by {@code project.environment} in the root; adding a third (a Kubernetes namespace
 * per run, a cloud stand) adds a class and a case, no test changes (DIP + OCP).
 */
public interface EnvironmentProvider {

    /**
     * Why this environment cannot be used, found <b>without</b> starting it: a missing Docker, an unbuilt image,
     * a stand that does not answer. Empty when it can be used. Asked before any test runs, to skip or fail with
     * the reason instead of a stack trace from the first client call.
     */
    Optional<String> unavailable();

    /** Starts what has to be started (once) and says where the SUT is. */
    SutEnvironment start();

    /**
     * Why event tests (kind {@code events}) cannot run here although the rest can: the environment has no broker
     * or it does not answer. Asked only for event tests, after {@link #unavailable()}. A capability, not a second
     * environment: a stand without Kafka still runs every other test.
     */
    Optional<String> eventsUnavailable();
}
