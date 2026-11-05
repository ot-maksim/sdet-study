package com.study.init.patterns.builder.inheritance;

public class User {
    private final String name;
    private final String surName;

    // Accepts any Builder subclass: UserBuilder, AdminBuilder, etc.
    protected User(Builder<?> b) {
        this.name = b.name;
        this.surName = b.surName;
    }

    public String getName() {
        return name;
    }

    public String getSurName() {
        return surName;
    }

    // The type is now known exactly — no <?> needed
    public static UserBuilder builder() {
        return new UserBuilder();
    }

    @Override
    public String toString() {
        return "User{" +
                "name='" + name + '\'' +
                ", surName='" + surName + '\'' +
                '}';
    }

    /**
     * Base builder "template": shared fields and methods.
     * Not instantiated directly (abstract) — only through subclasses,
     * each of which passes itself as T.
     */
    protected abstract static class Builder<T extends Builder<T>> {
        private String name;
        private String surName;

        // Each subclass returns itself — no (T) this cast needed
        protected abstract T self();

        public abstract User build();

        public T name(String name) {
            this.name = name;
            return self();
        }

        public T surName(String surName) {
            this.surName = surName;
            return self();
        }
    }

    /**
     * Concrete builder for User: T = UserBuilder.
     */
    public static class UserBuilder extends Builder<UserBuilder> {
        @Override
        protected UserBuilder self() {
            return this;
        }

        @Override
        public User build() {
            return new User(this);
        }
    }
}
