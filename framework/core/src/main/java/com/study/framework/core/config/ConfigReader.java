package com.study.framework.core.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Typed reads for {@link Config#bind}. A failed read does not throw: it records the problem and returns a
 * placeholder, so one bind reports every missing or malformed key at once. There are no default arguments:
 * defaults live in {@code framework.properties}, where {@link Config#describe()} shows them.
 */
public final class ConfigReader {

    private static final Pattern SHORT_DURATION = Pattern.compile("(\\d+)\\s*(ms|s|m|h)");

    private final Config config;
    private final List<String> problems = new ArrayList<>();

    ConfigReader(Config config) {
        this.config = config;
    }

    public String string(String key) {
        return raw(key).orElse(null);
    }

    public Secret secret(String key) {
        return raw(key).map(Secret::new).orElse(null);
    }

    public URI uri(String key) {
        // "localhost:7070" is an absolute URI too (scheme "localhost"), so a host is required as well
        return parse(key, "an absolute URI with a host", value -> {
            URI uri = new URI(value);
            if (!uri.isAbsolute() || uri.getHost() == null) {
                throw new URISyntaxException(value, "no scheme or no host");
            }
            return uri;
        });
    }

    public int integer(String key) {
        Integer value = parse(key, "an integer", Integer::valueOf);
        return value == null ? 0 : value;
    }

    public boolean bool(String key) {
        Boolean value = parse(key, "true or false", v -> switch (v) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException(v);
        });
        return value != null && value;
    }

    /** {@code 500ms}, {@code 5s}, {@code 2m}, {@code 1h} or ISO-8601 {@code PT5S}. */
    public Duration duration(String key) {
        return parse(key, "a duration like 500ms, 5s, 2m or PT5S", value -> {
            Matcher m = SHORT_DURATION.matcher(value);
            if (!m.matches()) {
                return Duration.parse(value);
            }
            long amount = Long.parseLong(m.group(1));
            return switch (m.group(2)) {
                case "ms" -> Duration.ofMillis(amount);
                case "s" -> Duration.ofSeconds(amount);
                case "m" -> Duration.ofMinutes(amount);
                default -> Duration.ofHours(amount);
            };
        });
    }

    List<String> problems() {
        return problems;
    }

    private Optional<String> raw(String key) {
        Optional<Config.Resolved> resolved = config.resolve(key);
        if (resolved.isEmpty()) {
            problems.add("missing " + key + " (looked in: " + config.lookupDescription(key) + ")");
            return Optional.empty();
        }
        return Optional.of(resolved.get().value());
    }

    @FunctionalInterface
    private interface Parser<T> {
        T parse(String value) throws Exception;
    }

    private <T> T parse(String key, String expected, Parser<T> parser) {
        Optional<Config.Resolved> resolved = config.resolve(key);
        if (resolved.isEmpty()) {
            raw(key);
            return null;
        }
        try {
            return parser.parse(resolved.get().value());
        } catch (Exception e) {
            problems.add(key + ": '" + resolved.get().value() + "' is not " + expected + " (from " + resolved.get().source() + ")");
            return null;
        }
    }
}
