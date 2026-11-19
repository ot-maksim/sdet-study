package com.study.review.bad.steps;

import java.util.HashMap;
import java.util.Map;

/** Shares data between steps of a scenario. */
public final class ScenarioContext {

    private static final Map<String, Object> data = new HashMap<>();

    private ScenarioContext() {
    }

    public static void put(String key, Object value) {
        data.put(key, value);
    }

    public static Object get(String key) {
        return data.get(key);
    }

    public static void clear() {
        data.clear();
    }
}
