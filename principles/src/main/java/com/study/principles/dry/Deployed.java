package com.study.principles.dry;

/**
 * The environment the tests run against: one deployed build, reached "by URL". Static on purpose — like a real
 * stand, it exists before and outside the test suite. The kata swaps the build to deploy regressions.
 */
public final class Deployed {

    private static volatile Release release = Release.CURRENT;
    private static volatile FakeServer server = new FakeServer(release);

    private Deployed() {
    }

    public static FakeServer http() {
        return server;
    }

    /**
     * The settings the app itself runs with. In real life: a constant like {@code UserValidator.MIN_AGE} in a
     * jar shared with the app, or a {@code /config} endpoint. Tests that read their expectations from here agree
     * with the app by construction.
     */
    public static Release release() {
        return release;
    }

    public static void deploy(Release build) {
        release = build;
        server = new FakeServer(build);
    }

    public static void reset() {
        deploy(Release.CURRENT);
    }
}
