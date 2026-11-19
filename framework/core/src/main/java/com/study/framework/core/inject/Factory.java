package com.study.framework.core.inject;

/** Builds one object by hand: {@code c -> new UserApi(c.get(ApiSettings.class))}. */
@FunctionalInterface
public interface Factory<T> {
    T create(Components components) throws Exception;
}
