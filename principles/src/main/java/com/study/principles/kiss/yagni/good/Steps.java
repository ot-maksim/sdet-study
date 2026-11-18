package com.study.principles.kiss.yagni.good;

/** Runs a body as a named step and tells the listener; the {@code step(..)} of v1, moved out. */
public final class Steps {

    private final StepListener listener;

    public Steps(StepListener listener) {
        this.listener = listener;
    }

    public void step(String name, Runnable body) {
        listener.started(name);
        try {
            body.run();
        } finally {
            listener.finished(name);
        }
    }

    public void step(String name) {
        step(name, () -> { });
    }
}
