package com.study.principles.kiss.yagni.good;

/**
 * Version 2: extracted when the second real consumer (the step tree) arrived, from what both of them need —
 * a start and an end. Not from a guess: there were two implementations to look at.
 */
public interface StepListener {

    void started(String step);

    void finished(String step);
}
