package com.study.template.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The observer mechanics: every listener gets the artifact, a broken one costs only itself. */
class ArtifactsTest {

    private final Artifact screenshot = new Artifact("UsersPageTest-createsUser()", "screenshot", "image/png", "png",
            new byte[] {1, 2, 3});

    @Test
    void aBrokenListenerDoesNotStopTheOthers() {
        List<Artifact> received = new ArrayList<>();
        Artifacts artifacts = new Artifacts(List.of(
                artifact -> {
                    throw new IllegalStateException("disk full");
                },
                received::add));

        artifacts.publish(screenshot);

        assertThat(received).containsExactly(screenshot);
    }

    @Test
    void filesGetANameSafeOnEverySystem(@TempDir Path directory) throws Exception {
        new ArtifactFiles(directory).artifactCreated(screenshot);

        assertThat(directory.resolve("UsersPageTest-createsUser_-screenshot.png")).exists();
        assertThat(Files.readAllBytes(directory.resolve(screenshot.fileName()))).containsExactly(1, 2, 3);
    }
}
