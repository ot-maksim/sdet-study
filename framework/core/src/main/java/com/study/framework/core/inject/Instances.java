package com.study.framework.core.inject;

import com.study.framework.core.lifecycle.Cleanup;
import com.study.framework.core.lifecycle.Scope;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Objects of one scope (the run or one test), created lazily — an API test never pays for a browser.
 * A TEST scope delegates RUN types to its parent. Lives in a JUnit Store, which closes it.
 *
 * <p>{@code synchronized} instead of {@code ConcurrentHashMap.computeIfAbsent}: a factory asks for its own
 * dependencies, i.e. re-enters {@link #get}, which {@code computeIfAbsent} forbids. Locks are taken
 * test → run, never the other way, so they cannot deadlock.
 */
final class Instances implements AutoCloseable {

    private final Lifetime lifetime;
    private final Bindings bindings;
    private final Instances parent;
    private final Scope scope;
    private final Map<Class<?>, Object> created = new HashMap<>();
    private final Deque<Class<?>> creating = new ArrayDeque<>();

    Instances(Lifetime lifetime, Bindings bindings, Instances parent, String name) {
        this.lifetime = lifetime;
        this.bindings = bindings;
        this.parent = parent;
        this.scope = new Scope(name);
    }

    synchronized <T> T get(Class<T> type) {
        Bindings.Binding<?> binding = bindings.get(type);
        if (binding == null) {
            throw new IllegalStateException("No binding for " + type.getName() + chain(type)
                    + "; bound: " + bindings.types().stream().map(Class::getSimpleName).collect(Collectors.joining(", ")));
        }
        if (binding.lifetime() == Lifetime.RUN && lifetime == Lifetime.TEST) {
            return parent.get(type);
        }
        if (binding.lifetime() == Lifetime.TEST && lifetime == Lifetime.RUN) {
            throw new IllegalStateException("Captive dependency" + chain(type) + ": " + creating.peek().getSimpleName()
                    + " lives for the whole run and would keep the first test's " + type.getSimpleName()
                    + ". Make it TEST-scoped too, or pass the " + type.getSimpleName() + " per call.");
        }
        Object existing = created.get(type);
        if (existing != null) {
            return type.cast(existing);
        }
        if (creating.contains(type)) {
            throw new IllegalStateException("Dependency cycle" + chain(type));
        }
        creating.push(type);
        try {
            Object object = binding.factory().create(components());
            if (object == null) {
                throw new IllegalStateException("Factory of " + type.getSimpleName() + " returned null");
            }
            created.put(type, object);
            return type.cast(object);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create " + type.getSimpleName() + chain(type) + ": " + e.getMessage(), e);
        } finally {
            creating.pop();
        }
    }

    Cleanup cleanup() {
        return scope;
    }

    @Override
    public void close() {
        scope.close();
    }

    private Components components() {
        return new Components() {
            @Override
            public <U> U get(Class<U> type) {
                return Instances.this.get(type);
            }

            @Override
            public Cleanup cleanup() {
                return scope;
            }
        };
    }

    /** " (UserSteps -> UserApi -> Page)" — the path that led to the problem. */
    private String chain(Class<?> type) {
        if (creating.isEmpty()) {
            return "";
        }
        List<String> path = new ArrayList<>();
        creating.descendingIterator().forEachRemaining(t -> path.add(t.getSimpleName()));
        path.add(type.getSimpleName());
        return " (" + String.join(" -> ", path) + ")";
    }
}
