package com.study.review.good.config;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The framework's configuration as a value: parsed once by the composition root, passed to whoever needs a part
 * of it. Every key is known: a typo or a missing value is an error at start, all problems at once — not a
 * silent default found weeks later. The full layered version: {@code framework/core} (ADR 0002).
 */
public record Settings(URI baseUrl, Duration timeout, Optional<DbSettings> db) {

    public static final Set<String> KEYS = Set.of("base.url", "timeout.ms", "db.url", "db.user", "db.password");

    public static Settings from(Map<String, String> values) {
        List<String> problems = new ArrayList<>();
        for (String key : new TreeSet<>(values.keySet())) {
            if (!KEYS.contains(key)) {
                problems.add("unknown key '" + key + "' (known: " + new TreeSet<>(KEYS) + ")");
            }
        }
        String baseUrl = values.get("base.url");
        if (baseUrl == null) {
            problems.add("'base.url' is required");
        }
        Duration timeout = Duration.ofSeconds(10);
        if (values.containsKey("timeout.ms")) {
            try {
                timeout = Duration.ofMillis(Long.parseLong(values.get("timeout.ms")));
            } catch (NumberFormatException e) {
                problems.add("'timeout.ms' must be a number of milliseconds: " + values.get("timeout.ms"));
            }
        }
        List<String> dbKeys = List.of("db.url", "db.user", "db.password");
        long dbKeysSet = dbKeys.stream().filter(values::containsKey).count();
        if (dbKeysSet != 0 && dbKeysSet != dbKeys.size()) {
            problems.add("database needs all of " + dbKeys + " or none");
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Invalid settings:\n  " + String.join("\n  ", problems));
        }
        Optional<DbSettings> db = dbKeysSet == 0
                ? Optional.empty()
                : Optional.of(new DbSettings(values.get("db.url"), values.get("db.user"), new Secret(values.get("db.password"))));
        return new Settings(URI.create(baseUrl), timeout, db);
    }

    public record DbSettings(String url, String user, Secret password) {
    }

    /** A value that never shows up in logs, reports or exception messages. */
    public record Secret(String value) {
        @Override
        public String toString() {
            return "****";
        }
    }
}
