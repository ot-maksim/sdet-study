package com.study.principles.ocp;

/** A launched (fake) browser: which engine, where it runs, how its screen is set up. */
public record Browser(String engine, String endpoint, Viewport viewport) {
}
