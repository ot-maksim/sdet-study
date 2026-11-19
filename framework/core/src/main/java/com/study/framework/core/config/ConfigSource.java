package com.study.framework.core.config;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One layer of configuration. Keys are lower-case and dotted: {@code sut.base-url}.
 *
 * @param name         shown in messages: where a value came from, where a key was looked for
 * @param values       the layer's raw values, looked up by the key as it is in this layer
 * @param keyMapping   how a framework key is spelled in this layer ({@code sut.base-url} → {@code SUT_BASE_URL})
 * @param declaresKeys {@code true} for files the framework owns: their keys are checked against the catalogue,
 *                     so a typo fails the run; process environment and system properties are not checked
 */
public record ConfigSource(String name, Map<String, String> values, KeyMapping keyMapping, boolean declaresKeys) {

    /** Spelling of a framework key in a source. */
    @FunctionalInterface
    public interface KeyMapping {
        String apply(String key);
    }

    public ConfigSource {
        values = Map.copyOf(values);
    }

    /** A file the framework owns (defaults, an environment); its keys are spelled as they are. */
    public static ConfigSource file(String name, Map<String, String> values) {
        return new ConfigSource(name, values, key -> key, true);
    }

    /** {@code -Dsut.base-url=...} */
    public static ConfigSource systemProperties(Map<String, String> properties) {
        return new ConfigSource("system properties", properties, key -> key, false);
    }

    /** {@code SUT_BASE_URL=...}: upper case, dots and dashes become underscores. */
    public static ConfigSource environmentVariables(Map<String, String> variables) {
        return new ConfigSource("environment variables", variables, ConfigSource::envName, false);
    }

    public static String envName(String key) {
        return key.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
    }

    Optional<String> get(String key) {
        return Optional.ofNullable(values.get(keyMapping.apply(key)));
    }

    /** Where to look for {@code key} in this layer, for "missing" messages. */
    String describeLookup(String key) {
        return name + " (" + keyMapping.apply(key) + ")";
    }

    Set<String> declaredKeys() {
        return declaresKeys ? values.keySet() : Set.of();
    }
}
