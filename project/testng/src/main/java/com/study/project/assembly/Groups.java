package com.study.project.assembly;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.testng.annotations.Test;

/**
 * Test kinds as TestNG groups. The JUnit twin has an annotation per kind ({@code @ApiTests}) that carries the tag and
 * the wiring at once (a meta-annotation). TestNG reads its annotations only where they are written: a kind cannot be
 * an annotation of ours, so it is a group on the class, and the wiring comes from {@link ProjectListener}, which
 * treats every class of a kind as a project test.
 *
 * <pre>
 * &#64;Test(groups = Groups.API)
 * public class UsersApiTest { ... }
 * </pre>
 *
 * Run one kind with {@code -Dgroups=api}. ArchitectureTest checks that a test class has exactly one kind.
 */
public final class Groups {

    /** API tests: the SUT through its REST API, checked against the database. */
    public static final String API = "api";

    /** UI tests: the users page in a browser (project.ui.profile), one leased browser per test. */
    public static final String UI = "ui";

    /** E2E tests: one business flow across API, database and page. */
    public static final String E2E = "e2e";

    /**
     * Tests that read events from Kafka (they may also use API, DB, page). Need a broker in the environment: without
     * one they are skipped with the reason (failed under {@code project.require-environment=true}), while the other
     * kinds still run against the same stand.
     */
    public static final String EVENTS = "events";

    /**
     * Not a kind: keeps a class that swaps global state (Allure's lifecycle) out of the parallel {@code <test>} of
     * {@code testng.xml}, which lists it by name in a {@code <test>} of its own that runs alone, first — JUnit's
     * {@code @Isolated} takes one annotation, here it takes the group and a line in the suite.
     */
    public static final String ISOLATED = "isolated";

    /** Every kind, in the order of the JUnit twin's annotations. */
    public static final List<String> KINDS = List.of(API, UI, E2E, EVENTS);

    private Groups() {
    }

    /** The kinds a test class declares in its class-level {@code @Test(groups = ...)}: one for a project test, none otherwise. */
    static Set<String> kindsOf(Class<?> testClass) {
        Test test = testClass.getAnnotation(Test.class);
        if (test == null) {
            return Set.of();
        }
        return Set.copyOf(Arrays.stream(test.groups()).filter(KINDS::contains).toList());
    }
}
