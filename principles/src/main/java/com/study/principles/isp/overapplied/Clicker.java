package com.study.principles.isp.overapplied;

/** One method, one interface — see {@link LoginPage} for what that does to clients. */
@FunctionalInterface
public interface Clicker {

    void click(String id);
}
