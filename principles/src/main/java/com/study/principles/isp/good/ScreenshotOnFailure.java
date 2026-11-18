package com.study.principles.isp.good;

public final class ScreenshotOnFailure {

    private final Screenshots screenshots;

    public ScreenshotOnFailure(Screenshots screenshots) {
        this.screenshots = screenshots;
    }

    public void run(String testName, Runnable test) {
        try {
            test.run();
        } catch (AssertionError | RuntimeException e) {
            screenshots.screenshot(testName);
            throw e;
        }
    }
}
