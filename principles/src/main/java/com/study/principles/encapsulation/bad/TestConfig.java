package com.study.principles.encapsulation.bad;

/**
 * BAD: a "config" that is a bag of raw values behind a getter and a setter per field, shared globally.
 *
 * <p>The fields are {@code private}, yet nothing is encapsulated:
 * <ul>
 *   <li><b>Representation leaks.</b> Clients get a raw {@code String} and a raw {@code int} and must
 *       know HOW to use them: is there a trailing slash? is the timeout in seconds or millis?
 *       Every client answers these questions on its own — and they answer differently.</li>
 *   <li><b>No freedom to change.</b> Switching to {@code URI}, adding a context path or changing the
 *       unit of the timeout breaks every {@code getBaseUrl() + "..."} in the code base.</li>
 *   <li><b>Global mutable state.</b> Any test can call a setter mid-run; every later test (and every
 *       parallel one) silently sees the change.</li>
 *   <li><b>No validation.</b> {@code setTimeout(-1)} or {@code setBaseUrl("localhost")} are accepted and
 *       blow up far from the cause. (Invariants are covered in depth in
 *       {@code com.study.principles.invariants}.)</li>
 * </ul>
 */
public class TestConfig {
    private static final TestConfig INSTANCE = new TestConfig();

    private String baseUrl = System.getProperty("baseUrl", "http://localhost:7070/");
    private int timeout = Integer.getInteger("timeout", 5);   // the author meant seconds; nothing says so

    public static TestConfig get() {
        return INSTANCE;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
}
