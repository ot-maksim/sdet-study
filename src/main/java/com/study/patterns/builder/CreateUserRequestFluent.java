package com.study.patterns.builder;
/*
Builder with a mutable result
The object is a plain class with setters (often a DTO for JSON serialization); the builder is just a convenient way to fill it.

A variant without a separate builder: fluent setters that return this. Formally it is not a builder,
but it solves the same problem for mutable objects:
public CreateUserRequest withName(String name) { this.name = name; return this; }
 */

public class CreateUserRequestFluent {
    private String name;
    private String email;

    public CreateUserRequestFluent() {
    }            // required by Jackson

    public String getName() {
        return name;
    }

    public CreateUserRequestFluent withName(String name) {
        this.name = name;
        return this;
    }

    public String getEmail() {
        return email;
    }

    public CreateUserRequestFluent withEmail(String email) {
        this.email = email;
        return this;
    }

    @Override
    public String toString() {
        return "CreateUserRequestFluent{" +
                "name='" + name + '\'' +
                ", email='" + email + '\'' +
                '}';
    }

    public static void main(String[] args) {
        CreateUserRequestFluent request = new CreateUserRequestFluent().withName("tom").withEmail("bad-email");
        System.out.println(request);
        request.withEmail(null);
        System.out.println(request);
    }
}
