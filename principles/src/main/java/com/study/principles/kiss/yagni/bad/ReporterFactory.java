package com.study.principles.kiss.yagni.bad;

import java.io.PrintStream;

/**
 * Creates a reporter from config, with room for the reporters "we will add later". The later ones are
 * placeholders; the one that did come later (a step tree) is none of them.
 */
public final class ReporterFactory {

    private final PrintStream out;

    public ReporterFactory(PrintStream out) {
        this.out = out;
    }

    public Reporter create(ReporterConfig config) {
        return switch (config.type()) {
            case "console" -> new ConsoleReporter(out);
            case "composite" -> new CompositeReporter(config.delegates().stream()
                    .map(type -> create(new ReporterConfig(type, java.util.List.of())))
                    .toList());
            case "file", "slack" -> throw new UnsupportedOperationException("planned: " + config.type());
            default -> throw new IllegalArgumentException("unknown reporter: " + config.type());
        };
    }
}
