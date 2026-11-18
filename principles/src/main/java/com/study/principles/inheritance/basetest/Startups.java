package com.study.principles.inheritance.basetest;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test probe, not a pattern: a log of what the fixtures started and stopped, so a test can see
 * what an API-only test paid for. Real frameworks see this as run time and grid slots.
 */
public final class Startups {

    private static final List<String> LOG = new CopyOnWriteArrayList<>();

    private Startups() {
    }

    public static void record(String what) {
        LOG.add(what);
    }

    public static List<String> log() {
        return List.copyOf(LOG);
    }

    public static void clear() {
        LOG.clear();
    }
}
