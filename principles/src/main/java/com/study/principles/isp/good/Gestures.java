package com.study.principles.isp.good;

/** Role: touch gestures. Native (and touch-emulated mobile web, if a client ever needs it). */
public interface Gestures {

    void tap(String id);

    void swipe(String direction);
}
