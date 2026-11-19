package com.study.framework.core.inject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The wiring table of a composition root: type → lifetime + hand-written factory. No scanning, no
 * reflection, no autowiring: every {@code new} is visible in one method ({@link CompositionRoot#bind}).
 * Types are matched exactly: binding {@code UserApi} does not make an {@code Object} parameter resolvable.
 */
public final class Bindings {

    record Binding<T>(Class<T> type, Lifetime lifetime, Factory<? extends T> factory) {
    }

    private final Map<Class<?>, Binding<?>> bindings = new LinkedHashMap<>();

    public <T> Bindings run(Class<T> type, Factory<? extends T> factory) {
        return add(new Binding<>(type, Lifetime.RUN, factory));
    }

    public <T> Bindings test(Class<T> type, Factory<? extends T> factory) {
        return add(new Binding<>(type, Lifetime.TEST, factory));
    }

    public boolean contains(Class<?> type) {
        return bindings.containsKey(type);
    }

    private Bindings add(Binding<?> binding) {
        if (bindings.putIfAbsent(binding.type(), binding) != null) {
            throw new IllegalStateException(binding.type().getSimpleName() + " is bound twice");
        }
        return this;
    }

    Binding<?> get(Class<?> type) {
        return bindings.get(type);
    }

    Set<Class<?>> types() {
        return bindings.keySet();
    }
}
