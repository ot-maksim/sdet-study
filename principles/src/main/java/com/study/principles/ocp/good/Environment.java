package com.study.principles.ocp.good;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Environments differ in <b>values</b>, not behaviour — so they are data, not a Strategy hierarchy.
 * A new environment is a new file {@code ocp/env/<name>.properties}; no class, no switch.
 * <p>
 * The dangerous flag is opt-in: a file without {@code destructive.allowed=true} is protected. Compare
 * {@code bad.EnvConfig}, where "everything except prod" is open.
 */
public record Environment(String name, String baseUrl, String dbUrl, boolean destructiveAllowed) {

    public static Environment load(String name) {
        String resource = "ocp/env/" + name + ".properties";
        try (InputStream in = Environment.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalArgumentException("unknown env '" + name + "': no " + resource + " on the class path");
            Properties p = new Properties();
            p.load(in);
            return new Environment(name, required(p, "base.url", resource), required(p, "db.url", resource),
                    Boolean.parseBoolean(p.getProperty("destructive.allowed", "false")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String required(Properties p, String key, String resource) {
        String value = p.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException(resource + ": missing " + key);
        return value;
    }
}
