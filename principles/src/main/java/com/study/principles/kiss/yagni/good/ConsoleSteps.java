package com.study.principles.kiss.yagni.good;

import java.io.PrintStream;

/** The console output of v1, as the first listener. */
public final class ConsoleSteps implements StepListener {

    private final PrintStream out;
    private int depth;

    public ConsoleSteps(PrintStream out) {
        this.out = out;
    }

    @Override
    public void started(String step) {
        out.println("  ".repeat(depth++) + "[STEP] " + step);
    }

    @Override
    public void finished(String step) {
        depth--;
    }
}
