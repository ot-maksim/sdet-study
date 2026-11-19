package com.study.template.scope;

import com.study.framework.core.lifecycle.Cleanup;
import com.study.framework.core.lifecycle.Scope;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The beans of one test and what has to be undone after it. Bean destruction (closing a {@code UiSession}) and the
 * steps' own undo actions (deleting a user) go to one {@link Scope}, so they run in one reverse order: the user
 * created after the browser is deleted before the browser closes. All of them run, failures are reported together.
 */
public final class TestBeans {

    private final String test;
    private final Scope cleanup;
    private final Map<String, Object> beans = new ConcurrentHashMap<>();

    public TestBeans(String test) {
        this.test = test;
        this.cleanup = new Scope(test);
    }

    public String test() {
        return test;
    }

    public Cleanup cleanup() {
        return cleanup;
    }

    /** The test's bean of {@code type} if it was already created, without creating it (a screenshot needs the browser only if the test had one). */
    public <T> Optional<T> peek(Class<T> type) {
        return beans.values().stream().filter(type::isInstance).map(type::cast).findFirst();
    }

    /** Ends the test: destroys its beans and runs its undo actions; throws {@code CleanupFailedException} listing every failure. */
    public void close() {
        beans.clear();
        cleanup.close();
    }

    Object get(String name) {
        return beans.get(name);
    }

    void put(String name, Object bean) {
        beans.put(name, bean);
    }

    Object remove(String name) {
        return beans.remove(name);
    }

    @Override
    public String toString() {
        return "TestBeans(" + test + ")";
    }
}
