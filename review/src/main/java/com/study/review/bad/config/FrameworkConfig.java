package com.study.review.bad.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/** Framework configuration: {@code config.properties}, overridden by system properties. */
public class FrameworkConfig {

    private static FrameworkConfig instance;

    private final Properties properties = new Properties();
    private final Set<String> fileKeys = new TreeSet<>();

    private FrameworkConfig() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException e) {
            System.out.println("Could not load config.properties: " + e.getMessage());
        }
        fileKeys.addAll(properties.stringPropertyNames());
        properties.putAll(System.getProperties());
        print();
    }

    public static FrameworkConfig getInstance() {
        if (instance == null) {
            instance = new FrameworkConfig();
        }
        return instance;
    }

    public String get(String key) {
        return properties.getProperty(key);
    }

    public String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String value = get(key);
        return value == null ? defaultValue : Integer.parseInt(value);
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key));
    }

    public void print() {
        System.out.println("===== Framework config =====");
        for (String key : fileKeys) {
            System.out.println(key + " = " + get(key));
        }
    }
}
