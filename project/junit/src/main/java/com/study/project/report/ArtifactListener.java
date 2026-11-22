package com.study.project.report;

/**
 * Observer of the artifacts a test run produces. The producer (the runner glue in {@code assembly}) knows this interface
 * only: whether the screenshot ends up in a folder, in Allure, or in both is the root's choice
 * ({@code ProjectConfiguration}: every bean of this type is subscribed).
 */
@FunctionalInterface
public interface ArtifactListener {

    /** Called on the test's thread, while the test is still running (its report entry is open). */
    void artifactCreated(Artifact artifact);
}
