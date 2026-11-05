package com.study.init.patterns.builder.inheritance;

public class User {
    private final String name;

    protected User(Builder<?> b) { this.name = b.name; }

    public String getName() { return name; }

    public static Builder<?> builder() { return new Builder<>(); }

    public static class Builder<T extends Builder<T>> {
        private String name;

        @SuppressWarnings("unchecked")
        protected T self() { return (T) this; }

        public T name(String name) { this.name = name; return self(); }
        public User build() { return new User(this); }
    }
}
