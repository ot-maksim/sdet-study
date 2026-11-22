package com.study.project.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;

/** The observer mechanics: every listener gets the artifact, a broken one costs only itself. */
public class ArtifactsTest {

    private final Artifact screenshot = new Artifact("UsersPageTest-createsUser()", "screenshot", "image/png", "png",
            new byte[] {1, 2, 3});

    @Test
    public void aBrokenListenerDoesNotStopTheOthers() {
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
    public void filesGetANameSafeOnEverySystem() throws Exception {
        Path directory = Files.createTempDirectory("artifacts-");   // TestNG has no @TempDir

        new ArtifactFiles(directory).artifactCreated(screenshot);

        assertThat(directory.resolve("UsersPageTest-createsUser_-screenshot.png")).exists();
        assertThat(Files.readAllBytes(directory.resolve(screenshot.fileName()))).containsExactly(1, 2, 3);
    }
}
