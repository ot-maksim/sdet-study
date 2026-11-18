package com.study.principles.lsp.fakes;

/**
 * Mutable entity, the way JPA/ORM entities usually are. {@code id} is null until the repository assigns one.
 */
public final class User {

    private final Long id;
    private final String email;
    private String name;

    public User(String email, String name) {
        this(null, email, name);
    }

    public User(Long id, String email, String name) {
        this.id = id;
        this.email = email;
        this.name = name;
    }

    public Long id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String name() {
        return name;
    }

    public void rename(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "User[" + id + ", " + email + ", " + name + "]";
    }
}
