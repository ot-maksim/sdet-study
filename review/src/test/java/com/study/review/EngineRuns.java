package com.study.review;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.Arrays;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.EngineExecutionResults;

/** Runs test classes through the Jupiter engine, as a separate run, and returns what happened. */
public final class EngineRuns {

    private EngineRuns() {
    }

    public static EngineExecutionResults run(Class<?>... classes) {
        return EngineTestKit.engine("junit-jupiter")
                .selectors(Arrays.stream(classes).map(c -> selectClass(c)).toArray(org.junit.platform.engine.DiscoverySelector[]::new))
                .execute();
    }

    /** Classes and methods concurrently, two threads: what a CI run with parallel execution does. */
    public static EngineExecutionResults runInParallel(Class<?>... classes) {
        return EngineTestKit.engine("junit-jupiter")
                .selectors(Arrays.stream(classes).map(c -> selectClass(c)).toArray(org.junit.platform.engine.DiscoverySelector[]::new))
                .configurationParameter("junit.jupiter.execution.parallel.enabled", "true")
                .configurationParameter("junit.jupiter.execution.parallel.mode.default", "concurrent")
                .configurationParameter("junit.jupiter.execution.parallel.mode.classes.default", "concurrent")
                .configurationParameter("junit.jupiter.execution.parallel.config.strategy", "fixed")
                .configurationParameter("junit.jupiter.execution.parallel.config.fixed.parallelism", "2")
                .execute();
    }
}
