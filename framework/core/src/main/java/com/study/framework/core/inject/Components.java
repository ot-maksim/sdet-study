package com.study.framework.core.inject;

import com.study.framework.core.lifecycle.Cleanup;

/** What a {@link Factory} sees while building one object: the other bindings and the cleanup of its lifetime. */
public interface Components {

    /** The object bound to {@code type}, created on first use in this scope. */
    <T> T get(Class<T> type);

    /** The cleanup of the scope being built: run scope for RUN bindings, the test's for TEST ones. */
    Cleanup cleanup();
}
