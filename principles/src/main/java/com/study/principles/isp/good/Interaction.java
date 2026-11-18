package com.study.principles.isp.good;

/**
 * Role: acting on elements of the current screen. What page objects need — and the same on web and native,
 * so one page object drives both. A cohesive set: clients that click also type and read.
 */
public interface Interaction {

    void click(String id);

    void type(String id, String text);

    String text(String id);
}
