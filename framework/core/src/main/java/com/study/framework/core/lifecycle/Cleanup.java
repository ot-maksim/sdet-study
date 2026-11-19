package com.study.framework.core.lifecycle;

/**
 * The role a step needs to undo what it created: "register the undo right after the do". Steps get this
 * interface, not the whole {@link Scope}; they cannot close it or see what others registered.
 *
 * <pre>
 *   User user = api.create(newUser);
 *   cleanup.defer("delete user " + user.id(), () -&gt; api.delete(user.id()));
 * </pre>
 */
public interface Cleanup {

    @FunctionalInterface
    interface Action {
        void run() throws Exception;
    }

    /** Runs {@code action} when the scope ends — after the test, also when it failed; last registered runs first. */
    void defer(String description, Action action);

    /** Closes {@code resource} when the scope ends and returns it, for {@code var page = cleanup.own(browser.newPage())}. */
    default <C extends AutoCloseable> C own(C resource) {
        defer("close " + resource, resource::close);
        return resource;
    }
}
