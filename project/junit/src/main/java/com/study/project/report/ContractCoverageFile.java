package com.study.project.report;

import com.study.project.api.ContractCoverage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the run's {@link ContractCoverage} when the run ends (the context closes): a Markdown file next to the
 * other results. Not a log line: the context closes in a shutdown hook, after Surefire stopped reading the output.
 * A report, not a verdict: nothing fails on a response nobody asked for — the end of a run is too late to fail a
 * test, and a partial run ({@code -Dgroups=ui}) would always look incomplete.
 *
 * <p>A context without a single API call (a test of the assembly itself) writes nothing, so it cannot overwrite the
 * run's report with an empty one.
 */
public final class ContractCoverageFile implements AutoCloseable {

    private final ContractCoverage coverage;
    private final Path file;

    public ContractCoverageFile(ContractCoverage coverage, Path file) {
        this.coverage = coverage;
        this.file = file;
    }

    @Override
    public void close() {
        if (coverage.calls() == 0) {
            return;
        }
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, coverage.report());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the contract coverage to " + file, e);
        }
    }
}
