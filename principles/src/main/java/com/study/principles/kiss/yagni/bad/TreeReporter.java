package com.study.principles.kiss.yagni.bad;

import com.study.principles.kiss.yagni.StepNode;

import java.util.ArrayList;
import java.util.List;

/**
 * The second reporter that really arrived: an Allure-like tree, steps inside steps. Squeezed into
 * {@link Reporter#report(String)} it only ever sees lines, so every step becomes a root. Doing it right means
 * changing {@code Reporter} — and with it every class built around the guess.
 */
public final class TreeReporter implements Reporter {

    private final List<StepNode> roots = new ArrayList<>();

    @Override
    public void report(String message) {
        roots.add(new StepNode(message));
    }

    public List<StepNode> roots() {
        return List.copyOf(roots);
    }
}
