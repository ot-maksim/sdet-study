package com.study.framework.core.config;

import java.util.List;

/** Every configuration problem of a run in one message, not one per restart. */
public class ConfigException extends RuntimeException {

    private final List<String> problems;

    public ConfigException(String env, List<String> problems) {
        super("Configuration problems (env=" + env + "):\n  - " + String.join("\n  - ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
