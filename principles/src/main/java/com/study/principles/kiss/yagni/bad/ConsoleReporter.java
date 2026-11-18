package com.study.principles.kiss.yagni.bad;

import java.io.PrintStream;

/** The one reporter that was actually needed. */
public final class ConsoleReporter implements Reporter {

    private final PrintStream out;

    public ConsoleReporter(PrintStream out) {
        this.out = out;
    }

    @Override
    public void report(String message) {
        out.println("[STEP] " + message);
    }
}
