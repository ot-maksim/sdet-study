package com.study.project.assembly;

import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/**
 * One more attempt for a failed test, in CI only: Surefire's {@code rerunFailingTestsCount} (what the JUnit twin
 * uses) does not work with the TestNG provider, so TestNG retries in-process. The count comes from the system property
 * {@value #PROPERTY}: {@code 0} locally, {@code 1} in the Maven profile {@code ci} (pom.xml). {@link ProjectListener}
 * attaches it to every test when the count is positive.
 *
 * <p>The failed attempt stays in the results as a skipped, "retried" one (TestNG, Surefire, Allure's retries tab); a
 * test that passed only on a retry is flaky and {@link ProjectListener} lists it.
 *
 * <p>TestNG makes one analyzer per test method; under {@code parallel="methods"} two invocations of one method do not
 * happen at once, so the counter needs no synchronization.
 */
public class ProjectRetry implements IRetryAnalyzer {

    public static final String PROPERTY = "rerun.count";

    private final int attempts;
    private int retried;

    /** What TestNG calls: as many retries as {@value #PROPERTY} says. */
    public ProjectRetry() {
        this(attempts());
    }

    protected ProjectRetry(int attempts) {
        this.attempts = attempts;
    }

    /** Retries allowed in this run. */
    static int attempts() {
        return Integer.getInteger(PROPERTY, 0);
    }

    @Override
    public boolean retry(ITestResult result) {
        if (retried >= attempts) {
            return false;
        }
        retried++;
        Throwable cause = result.getThrowable();
        System.err.println("Retrying " + ProjectListener.name(result) + " (" + retried + "/" + attempts + ") after: "
                + (cause == null ? "?" : String.valueOf(cause.getMessage()).lines().findFirst().orElse("")));
        return true;
    }

    /** Whether the test this analyzer belongs to has already been retried: a pass after that is flaky. */
    boolean wasRetried() {
        return retried > 0;
    }
}
