package com.study.principles.isp.bad;

/** Uses one method of {@link Driver}; its unit test needs a fake with all 13 (see the test sources). */
public final class ScreenshotOnFailure {

    private final Driver driver;

    public ScreenshotOnFailure(Driver driver) {
        this.driver = driver;
    }

    public void run(String testName, Runnable test) {
        try {
            test.run();
        } catch (AssertionError | RuntimeException e) {
            driver.screenshot(testName);
            throw e;
        }
    }
}
