package com.study.principles.invariants.hashkey.bad;

import java.util.Objects;

/**
 * BAD: a mutable test user whose identity ({@code equals}/{@code hashCode}) depends on a field
 * that has a setter.
 *
 * <h2>Problem</h2>
 * Used as a key in a {@link java.util.HashMap} (e.g. user → auth token cache), it breaks the map's
 * invariant as soon as {@link #setLogin(String)} is called after insertion:
 * <ul>
 *   <li>{@code map.get(user)} returns {@code null} although it is the very same object;</li>
 *   <li>{@code map.containsKey(user)} returns {@code false};</li>
 *   <li>{@code map.size()} still counts the entry — it is there, but unreachable (a memory leak too).</li>
 * </ul>
 * The test then fails with a NullPointerException on the missing token, and the stack trace points
 * nowhere near the {@code setLogin} call that caused it.
 * <p>
 * Rule: fields used in {@code equals}/{@code hashCode} must not change while the object is in a
 * hash-based collection. The simplest way to guarantee that is to make them immutable.
 *
 * @see com.study.principles.invariants.hashkey.good.TestUser the fixed version
 */
public class TestUser {
    private String login;

    public TestUser(String login) {
        this.login = login;
    }

    public String getLogin() {
        return login;
    }

    /** Changes the identity of the object — dangerous for an object used as a map key. */
    public void setLogin(String login) {
        this.login = login;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TestUser other && Objects.equals(login, other.login);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(login);
    }

    @Override
    public String toString() {
        return "TestUser[login=" + login + "]";
    }
}
