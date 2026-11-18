package com.study.principles.inheritance.pages.good;

/** A component: one widget, one place for its selectors. Device differences are implementations. */
public interface Header {

    String userName();

    void logout();
}
