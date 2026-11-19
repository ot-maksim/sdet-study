package com.study.framework.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Layered, immutable configuration of one run. Layers, highest first:
 * <ol>
 *   <li>system properties: {@code -Dsut.base-url=...}</li>
 *   <li>environment variables: {@code SUT_BASE_URL=...}</li>
 *   <li>{@code env/<env>.properties}: the stand, chosen by {@code -Dframework.env} / {@code FRAMEWORK_ENV}, default {@code local}</li>
 *   <li>{@code framework.properties}: defaults; every module brings its own file with its own keys</li>
 * </ol>
 * The defaults files are the <b>catalogue</b>: a key unknown to them in an env file is a typo and fails the
 * run. A blank value counts as missing, so a required key without a default is declared empty
 * ({@code db.password=}) and must come from a higher layer.
 *
 * <p>Modules read their keys into their own settings record through {@link #bind}; core knows no module keys
 * (adding a module adds a defaults file, not a change here).
 */
public final class Config {

    public static final String ENV_KEY = "framework.env";
    static final String DEFAULTS = "framework.properties";

    private static final Pattern SECRET_KEY = Pattern.compile(".*(password|secret|token|key)$");

    private final String env;
    private final List<ConfigSource> layers;   // highest first
    private final Set<String> catalogue;

    private Config(String env, List<ConfigSource> layers) {
        this.env = env;
        this.layers = List.copyOf(layers);
        this.catalogue = Collections.unmodifiableSet(new TreeSet<>(layers.get(layers.size() - 1).declaredKeys()));
        List<String> problems = new ArrayList<>();
        for (ConfigSource layer : this.layers.subList(0, layers.size() - 1)) {
            for (String key : layer.declaredKeys()) {
                if (!catalogue.contains(key)) {
                    problems.add("unknown key " + key + " in " + layer.name() + "; known keys: " + catalogue);
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new ConfigException(env, problems);
        }
    }

    /** The run's configuration from this process: system properties, environment, class path. */
    public static Config load() {
        Map<String, String> properties = new HashMap<>();
        System.getProperties().forEach((k, v) -> properties.put(String.valueOf(k), String.valueOf(v)));
        return load(properties, System.getenv(), Thread.currentThread().getContextClassLoader());
    }

    public static Config load(Map<String, String> systemProperties, Map<String, String> environment, ClassLoader classLoader) {
        ConfigSource system = ConfigSource.systemProperties(systemProperties);
        ConfigSource variables = ConfigSource.environmentVariables(environment);
        String env = system.get(ENV_KEY).or(() -> variables.get(ENV_KEY)).filter(v -> !v.isBlank()).orElse("local");

        ConfigSource envFile = merged("env/" + env + ".properties", classLoader)
                .orElseThrow(() -> new ConfigException(env, List.of("no env/" + env + ".properties on the class path ("
                        + ENV_KEY + " selects the stand)")));
        ConfigSource defaults = merged(DEFAULTS, classLoader)
                .orElseThrow(() -> new ConfigException(env, List.of("no " + DEFAULTS + " on the class path")));
        return new Config(env, List.of(system, variables, envFile, defaults));
    }

    /** For tests of modules: explicit layers, highest first; the last one is the catalogue. */
    public static Config of(String env, List<ConfigSource> layersHighestFirst) {
        return new Config(env, layersHighestFirst);
    }

    public String env() {
        return env;
    }

    /**
     * Reads a module's settings. All problems of the binder are reported together; a binder that fails on a
     * placeholder (a {@code null} from a missing key) still reports the config problems, not its NPE.
     */
    public <T> T bind(Function<ConfigReader, T> binder) {
        ConfigReader reader = new ConfigReader(this);
        T result;
        try {
            result = binder.apply(reader);
        } catch (RuntimeException e) {
            if (reader.problems().isEmpty()) {
                throw e;
            }
            throw new ConfigException(env, reader.problems());
        }
        if (!reader.problems().isEmpty()) {
            throw new ConfigException(env, reader.problems());
        }
        return result;
    }

    /** Every catalogue key with its value and origin, secrets masked: print it at the start of a run. */
    public String describe() {
        StringBuilder out = new StringBuilder("Configuration (env=" + env + "):");
        for (String key : catalogue) {
            Optional<Resolved> resolved = resolve(key);
            String value = resolved.map(r -> isSecret(key) ? "****" : r.value()).orElse("<missing>");
            out.append("\n  ").append(key).append(" = ").append(value)
                    .append(resolved.map(r -> "   [" + r.source() + "]").orElse(""));
        }
        return out.toString();
    }

    @Override
    public String toString() {
        return "Config(env=" + env + ")";
    }

    record Resolved(String value, String source) {
    }

    Optional<Resolved> resolve(String key) {
        for (ConfigSource layer : layers) {
            Optional<String> value = layer.get(key);
            if (value.isPresent()) {
                return value.filter(v -> !v.isBlank()).map(v -> new Resolved(v.strip(), layer.name()));
            }
        }
        return Optional.empty();
    }

    String lookupDescription(String key) {
        List<String> places = new ArrayList<>();
        layers.forEach(layer -> places.add(layer.describeLookup(key)));
        return String.join(", ", places);
    }

    private static boolean isSecret(String key) {
        return SECRET_KEY.matcher(key).matches();
    }

    /** All resources with this name (one per module), merged; the same key in two of them is an error. */
    private static Optional<ConfigSource> merged(String resource, ClassLoader classLoader) {
        try {
            List<URL> urls = Collections.list(classLoader.getResources(resource));
            if (urls.isEmpty()) {
                return Optional.empty();
            }
            Map<String, String> values = new HashMap<>();
            Map<String, URL> owners = new HashMap<>();
            for (URL url : urls) {
                Properties properties = new Properties();
                try (InputStream in = url.openStream()) {
                    properties.load(in);
                }
                for (String key : properties.stringPropertyNames()) {
                    URL previous = owners.putIfAbsent(key, url);
                    if (previous != null) {
                        throw new IllegalStateException("key " + key + " is declared twice: " + previous + " and " + url);
                    }
                    values.put(key, properties.getProperty(key));
                }
            }
            return Optional.of(ConfigSource.file(resource, values));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
