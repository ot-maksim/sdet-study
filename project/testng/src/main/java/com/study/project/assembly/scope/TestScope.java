package com.study.project.assembly.scope;

import com.study.project.core.lifecycle.Cleanup;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;

/**
 * Spring has singletons and prototypes, web scopes (request, session) — and no "one per test method"; here it is a
 * custom Spring {@link Scope}.
 *
 * <p>Spring's {@code Scope.get} does not say <i>which</i> test asks, so the current test is bound to the thread —
 * but only for the moment of resolving a test's parameters ({@link #within}), not for the whole test. The usual
 * way (Cucumber's glue scope, a thread scope) binds it for the whole test and inherits the {@code ThreadLocal}
 * pitfalls (principles/parallel): a step in another thread silently gets another instance or none. Here every
 * request outside that window fails with one message — a field, {@code @BeforeAll}, a singleton that holds a
 * test bean (captive dependency, see {@link CaptiveDependencyGuard}), another thread.
 */
public final class TestScope implements Scope {

    public static final String NAME = "test";

    private static final ThreadLocal<TestBeans> BOUND = new ThreadLocal<>();

    /** Runs {@code action} (a {@code getBean}) with {@code beans} as the current test of this thread. */
    public static <T> T within(TestBeans beans, Supplier<T> action) {
        TestBeans previous = BOUND.get();
        if (previous != null && previous != beans) {
            throw new IllegalStateException("Resolving beans of " + beans + " while " + previous + " is bound to this thread");
        }
        BOUND.set(beans);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                BOUND.remove();
            }
        }
    }

    /** The current test's cleanup: the factory method of the {@code Cleanup} bean. */
    public static Cleanup currentCleanup() {
        return bound("cleanup").cleanup();
    }

    @Override
    public Object get(String name, ObjectFactory<?> factory) {
        TestBeans beans = bound(name);
        Object bean = beans.get(name);
        if (bean == null) {
            // Not computeIfAbsent: the factory resolves the bean's own test-scoped dependencies, i.e. re-enters here
            bean = factory.getObject();
            beans.put(name, bean);
        }
        return bean;
    }

    @Override
    public Object remove(String name) {
        TestBeans beans = BOUND.get();
        return beans == null ? null : beans.remove(name);
    }

    /** Spring's destroy method of a test bean ({@code UiSession.close}) becomes one more undo action of the test. */
    @Override
    public void registerDestructionCallback(String name, Runnable callback) {
        bound(name).cleanup().defer("destroy test bean " + name, callback::run);
    }

    @Override
    public Object resolveContextualObject(String key) {
        return null;
    }

    @Override
    public String getConversationId() {
        TestBeans beans = BOUND.get();
        return beans == null ? null : beans.test();
    }

    private static TestBeans bound(String name) {
        TestBeans beans = BOUND.get();
        if (beans == null) {
            throw new IllegalStateException("'" + name + "' lives per test, but it was asked for outside a test method"
                    + " (thread " + Thread.currentThread().getName() + "). Ask for it as a parameter of the test method —"
                    + " not in a field, in a once-per-class setup (@BeforeAll, @BeforeClass), in a run-wide bean or from another thread.");
        }
        return beans;
    }
}
