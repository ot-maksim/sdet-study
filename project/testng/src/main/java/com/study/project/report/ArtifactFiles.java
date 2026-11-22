package com.study.project.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Artifacts as files (CI uploads the folder): what a developer opens without a report. */
public final class ArtifactFiles implements ArtifactListener {

    private final Path directory;

    public ArtifactFiles(Path directory) {
        this.directory = directory;
    }

    @Override
    public void artifactCreated(Artifact artifact) {
        Path file = directory.resolve(artifact.fileName());
        try {
            Files.createDirectories(directory);
            Files.write(file, artifact.content());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
        System.err.println(artifact.name() + " of the failed test: " + file.toAbsolutePath());
    }
}
