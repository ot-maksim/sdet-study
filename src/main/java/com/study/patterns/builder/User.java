package com.study.patterns.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.util.Arrays.asList;

/*
Bloch's builder with an immutable result
Same as above, slightly improved: required fields, validation and a defensive copy of the collection.
What makes the object immutable: final fields, a private constructor, no setters, List.copyOf instead of a reference to a mutable list.
Without the last point the object is immutable only formally: the list could be changed from outside.

toBuilder() is very useful for test data: take a "valid default user" and change one field for a negative case.
 */

public final class User {
    private final String name;          // required
    private final String email;         // required
    private final int age;              // optional
    private final List<String> roles;   // optional

    private User(Builder b) {
        this.name = b.name;
        this.email = b.email;
        this.age = b.age;
        this.roles = List.copyOf(b.roles);   // immutable copy
    }

    public static Builder builder(String name, String email) {
        return new Builder(name, email);
    }

    public Builder toBuilder() {             // copy with changes
        return new Builder(name, email).age(age).roles(roles);
    }

    // getters only, no setters
    public String getName() {
        return name;
    }

    public List<String> getRoles() {
        return roles;
    }

    @Override
    public String toString() {
        return "User{" +
                "name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", age=" + age +
                ", roles=" + roles +
                '}';
    }

    public static final class Builder {
        private final String name;
        private final String email;
        private int age;
        private List<String> roles = new ArrayList<>();

        private Builder(String name, String email) {
            this.name = Objects.requireNonNull(name);
            this.email = Objects.requireNonNull(email);
        }

        public Builder age(int age) {
            this.age = age;
            return this;
        }

        public Builder roles(List<String> roles) {
            this.roles = new ArrayList<>(roles);
            return this;
        }

        public Builder role(String role) {
            this.roles.add(role);
            return this;
        }

        public User build() {
            if (age < 0) throw new IllegalStateException("age must be >= 0");
            return new User(this);
        }
    }

    public static void main(String[] args) {
        User admin = User.builder("tom", "tom@test.com").age(30).roles(asList("A", "B")).role("ADMIN").build();
        User guest = admin.toBuilder().age(31).build();

        System.out.println(admin);
        System.out.println(guest);
    }
}
