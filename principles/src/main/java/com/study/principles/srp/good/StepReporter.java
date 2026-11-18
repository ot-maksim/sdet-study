package com.study.principles.srp.good;

/** Where steps go: console now, Allure later (stage 4). One method, so a lambda or {@code list::add} fits. */
@FunctionalInterface
public interface StepReporter {

    void step(String description);

    static StepReporter console() {
        return description -> System.out.println("[STEP] " + description);
    }
}
