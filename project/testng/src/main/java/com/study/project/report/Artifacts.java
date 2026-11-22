package com.study.project.report;

import java.util.List;

/**
 * The subject: hands every artifact to every listener. One listener failing (a full disk, Allure without a
 * running test) must neither hide the artifact from the others nor replace the test's own failure in the report —
 * so each call is isolated and only printed.
 */
public final class Artifacts {

    private final List<ArtifactListener> listeners;

    public Artifacts(List<ArtifactListener> listeners) {
        this.listeners = List.copyOf(listeners);
    }

    public void publish(Artifact artifact) {
        for (ArtifactListener listener : listeners) {
            try {
                listener.artifactCreated(artifact);
            } catch (RuntimeException e) {
                System.err.println("Artifact " + artifact.fileName() + " not delivered to "
                        + listener.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }
}
