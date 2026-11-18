package com.study.principles.isp.overapplied;

/** One method, one interface — see {@link LoginPage} for what that does to clients. */
@FunctionalInterface
public interface Typer {

    void type(String id, String text);
}
