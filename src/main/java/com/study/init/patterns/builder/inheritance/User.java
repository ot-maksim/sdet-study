package com.study.init.patterns.builder.inheritance;

public class User {
    private final String name;
    private final String surName;

    protected User(UserBuilder<?> b) {
        this.name = b.name;
        this.surName = b.surName;
    }

    public String getName() {
        return name;
    }

    public String getSurName() {
        return surName;
    }

    public static UserBuilder<?> builder() {
        return new UserBuilder<>();
    }

    @Override
    public String toString() {
        return "User{" +
                "name='" + name + '\'' +
                ", surName='" + surName + '\'' +
                '}';
    }

    public static class UserBuilder<T extends UserBuilder<T>> {
        private String name;
        private String surName;

        @SuppressWarnings("unchecked")
        protected T self() {
            return (T) this;
        }

        public T name(String name) {
            this.name = name;
            return self();
        }

        public T surName(String surName) {
            this.surName = surName;
            return self();
        }

        public User build() {
            return new User(this);
        }
    }
}
