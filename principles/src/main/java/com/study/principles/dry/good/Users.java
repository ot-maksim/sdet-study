package com.study.principles.dry.good;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test data builder: valid defaults that no test depends on, and a method per field a test may care about.
 * The test states only the difference — {@code aUser().aged(17)} — and that difference is the story.
 */
public final class Users {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private Users() {
    }

    public static Builder aUser() {
        return new Builder();
    }

    public static final class Builder {
        private String name = "Test User";
        private String email = "user-" + SEQUENCE.incrementAndGet() + "@test.example";
        private Integer age = 30;
        private String country = "DE";

        private Builder() {
        }

        public Builder named(String name) {
            this.name = name;
            return this;
        }

        public Builder withEmail(String email) {
            this.email = email;
            return this;
        }

        public Builder aged(Integer age) {
            this.age = age;
            return this;
        }

        public Builder from(String country) {
            this.country = country;
            return this;
        }

        public NewUser build() {
            return new NewUser(name, email, age, country);
        }
    }
}
