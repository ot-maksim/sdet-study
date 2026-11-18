package com.study.principles.dry.bad;

import java.util.HashMap;
import java.util.Map;

/**
 * "One place for test data". The template is shared and mutable: {@link #validUser()} hands out the template
 * itself, so a test can "just tweak one field" — and every later test inherits the tweak.
 */
public final class TestData {

    private static final Map<String, Object> VALID_USER = new HashMap<>();

    static {
        restoreTemplate();
    }

    private TestData() {
    }

    /** The template, ready to tweak. Saves a copy — and a line in every test. */
    public static Map<String, Object> validUser() {
        return VALID_USER;
    }

    /**
     * Kata only: lets the kata's own tests start from a clean template. A real suite has no such hook, which is
     * the point — the tweak lives until the JVM exits, and the failure lands on whoever runs next.
     */
    public static void restoreTemplate() {
        VALID_USER.clear();
        VALID_USER.put("name", "John Doe");
        VALID_USER.put("email", "john.doe@test.com");
        VALID_USER.put("age", 30);
        VALID_USER.put("country", "DE");
    }
}
