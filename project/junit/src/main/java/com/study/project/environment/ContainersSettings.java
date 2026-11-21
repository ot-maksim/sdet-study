package com.study.project.environment;

import java.nio.file.Path;

/**
 * What the run starts ({@code project.containers.*}). {@code sutBuildDir}: the SUT's Dockerfile and
 * {@code target/sut.jar}; in a real project, an image from a registry instead.
 */
public record ContainersSettings(String postgresImage, String kafkaImage, Path sutBuildDir) {
}
