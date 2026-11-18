package com.study.principles.coupling;

/**
 * The slice of a browser that page objects need (think Playwright {@code Page}). Pages depend on this
 * interface; only the composition root knows which implementation is behind it.
 */
public interface Browser extends AutoCloseable {

    void open(String path);

    void fill(String field, String value);

    void click(String button);

    String currentPath();

    /** Text of the element with this id; fails if it is not on the current page. */
    String text(String id);

    boolean isClosed();

    @Override
    void close();
}
