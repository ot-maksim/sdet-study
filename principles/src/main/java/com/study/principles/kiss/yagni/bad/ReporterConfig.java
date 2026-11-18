package com.study.principles.kiss.yagni.bad;

import java.util.List;

/** Read from {@code reporting.type} / {@code reporting.delegates}. Every suite sets {@code console}. */
public record ReporterConfig(String type, List<String> delegates) {

    public static final ReporterConfig DEFAULT = new ReporterConfig("console", List.of());
}
