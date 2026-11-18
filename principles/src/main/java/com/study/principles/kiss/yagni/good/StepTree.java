package com.study.principles.kiss.yagni.good;

import com.study.principles.kiss.yagni.StepNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** The second real listener: an Allure-like tree. */
public final class StepTree implements StepListener {

    private final List<StepNode> roots = new ArrayList<>();
    private final Deque<StepNode> open = new ArrayDeque<>();

    @Override
    public void started(String step) {
        StepNode node = new StepNode(step);
        if (open.isEmpty()) {
            roots.add(node);
        } else {
            open.peek().children().add(node);
        }
        open.push(node);
    }

    @Override
    public void finished(String step) {
        open.pop();
    }

    public List<StepNode> roots() {
        return List.copyOf(roots);
    }
}
