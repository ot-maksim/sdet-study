package com.study.project.assembly;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;

/**
 * Tests that read events from Kafka (they may also use API, DB, page). Need a broker in the environment: without
 * one they are skipped with the reason (failed under {@code project.require-environment=true}), while the other
 * kinds still run against the same stand. Run only these with {@code -Dgroups=events}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("events")
@ProjectTest
public @interface EventTests {
}
