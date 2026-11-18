package com.study.principles.kiss.yagni.bad;

import java.util.List;

/** "For when we report to several places at once." Never configured by any suite. */
public final class CompositeReporter implements Reporter {

    private final List<Reporter> delegates;

    public CompositeReporter(List<Reporter> delegates) {
        this.delegates = List.copyOf(delegates);
    }

    @Override
    public void report(String message) {
        delegates.forEach(r -> r.report(message));
    }

    @Override
    public void flush() {
        delegates.forEach(Reporter::flush);
    }
}
