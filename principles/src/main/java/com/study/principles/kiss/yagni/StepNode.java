package com.study.principles.kiss.yagni;

import java.util.ArrayList;
import java.util.List;

/** A node of an Allure-like step tree: a step and the steps run inside it. */
public record StepNode(String name, List<StepNode> children) {

    public StepNode(String name) {
        this(name, new ArrayList<>());
    }

    public int depth() {
        return 1 + children.stream().mapToInt(StepNode::depth).max().orElse(0);
    }

    /** {@code buy > [add to cart, pay > [confirm 3DS]]}. */
    @Override
    public String toString() {
        return children.isEmpty() ? name : name + " > " + children;
    }
}
