package com.study.principles.encapsulation.good;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * GOOD: an immutable config that answers WHAT clients need and hides HOW it is stored.
 *
 * <h2>Abstraction: the public API is phrased in clients' terms</h2>
 * <ul>
 *   <li>{@link #endpoint(String)} — "where is {@code /api/users/1}?" Clients never see the base URL,
 *       so they cannot concatenate it wrongly. Slashes, context path, scheme are this class's problem.</li>
 *   <li>{@link #timeout()} — a {@link Duration}: the unit is part of the type, not of a comment.</li>
 * </ul>
 *
 * <h2>Encapsulation: the decisions likely to change are hidden (information hiding, Parnas)</h2>
 * <ul>
 *   <li>property names ({@code baseUrl}, {@code timeout.ms}) live only here;</li>
 *   <li>where values come from (system properties, env, file) is decided by the factory;</li>
 *   <li>the internal representation ({@code URI} ending with "/") can change without touching clients.</li>
 * </ul>
 * This is why encapsulation matters — not "security" (reflection reads private fields anyway), but
 * protected invariants and the freedom to change the implementation.
 *
 * <h2>Immutability</h2>
 * Nobody can change the config mid-run. A test that needs another host asks for a modified copy
 * ({@link #withBaseUrl(String)}) and the shared instance stays intact. Immutable objects are also
 * safe to share between parallel tests without synchronization.
 *
 * <h2>Why a class and not a record</h2>
 * A record's API <i>is</i> its representation: {@code record TestConfig(URI base, Duration timeout)}
 * would publish {@code base()}, and clients would be back to {@code base() + "login"}.
 * Records fit data (DTOs, test data, keys); here the point is to hide the data behind behaviour.
 */
public final class TestConfig {
    static final String BASE_URL = "baseUrl";
    static final String TIMEOUT_MS = "timeout.ms";

    private static final String DEFAULT_BASE_URL = "http://localhost:7070";
    private static final String DEFAULT_TIMEOUT_MS = "5000";

    private final URI base;           // invariant: absolute http(s), path always ends with "/"
    private final Duration timeout;   // invariant: > 0

    private TestConfig(URI base, Duration timeout) {
        this.base = base;
        this.timeout = timeout;
    }

    public static TestConfig fromSystemProperties() {
        return from(toMap(System.getProperties()));
    }

    /**
     * Parses and validates everything at once and fails fast with ALL problems in one message —
     * at startup, not in the middle of the 200th test.
     */
    public static TestConfig from(Map<String, String> properties) {
        List<String> errors = new ArrayList<>();
        URI base = parseBase(properties.getOrDefault(BASE_URL, DEFAULT_BASE_URL), errors);
        Duration timeout = parseTimeout(properties.getOrDefault(TIMEOUT_MS, DEFAULT_TIMEOUT_MS), errors);
        failIfAny(errors);
        return new TestConfig(base, timeout);
    }

    /** Absolute address of a path in the SUT; a leading slash is optional. */
    public URI endpoint(String path) {
        Objects.requireNonNull(path, "path");
        return base.resolve(path.replaceFirst("^/+", ""));
    }

    public Duration timeout() {
        return timeout;
    }

    /** Scoped override: a new config for one test; this instance is unchanged. */
    public TestConfig withBaseUrl(String baseUrl) {
        List<String> errors = new ArrayList<>();
        URI newBase = parseBase(baseUrl, errors);
        failIfAny(errors);
        return new TestConfig(newBase, timeout);
    }

    @Override
    public String toString() {
        return "TestConfig[base=" + base + ", timeout=" + timeout + "]";
    }

    // --- HOW: parsing and normalization, invisible to clients ---

    private static URI parseBase(String raw, List<String> errors) {
        if (raw == null || raw.isBlank()) {
            errors.add(BASE_URL + " is empty");
            return null;
        }
        String normalized = raw.trim().replaceFirst("/+$", "") + "/";
        try {
            URI uri = new URI(normalized);
            boolean http = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
            if (!http || uri.getHost() == null || uri.getQuery() != null || uri.getFragment() != null) {
                errors.add(BASE_URL + " '" + raw + "' must be an absolute http(s) URL without query/fragment");
                return null;
            }
            return uri;
        } catch (URISyntaxException e) {
            errors.add(BASE_URL + " '" + raw + "' is not a URL: " + e.getReason());
            return null;
        }
    }

    private static Duration parseTimeout(String raw, List<String> errors) {
        try {
            long millis = Long.parseLong(raw.trim());
            if (millis <= 0) {
                errors.add(TIMEOUT_MS + " must be > 0, was " + millis);
                return null;
            }
            return Duration.ofMillis(millis);
        } catch (NumberFormatException e) {
            errors.add(TIMEOUT_MS + " '" + raw + "' is not a number of milliseconds");
            return null;
        }
    }

    private static void failIfAny(List<String> errors) {
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid test config:\n - " + String.join("\n - ", errors));
        }
    }

    private static Map<String, String> toMap(Properties properties) {
        Map<String, String> map = new java.util.HashMap<>();
        properties.stringPropertyNames().forEach(name -> map.put(name, properties.getProperty(name)));
        return map;
    }
}
