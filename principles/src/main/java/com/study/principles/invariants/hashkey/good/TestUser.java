package com.study.principles.invariants.hashkey.good;

import java.util.Objects;

/**
 * GOOD: an immutable test user, safe to use as a {@link java.util.HashMap} key.
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li><b>{@code record}</b> — fields are {@code private final}, there are no setters, the class is
 *       {@code final}, and {@code equals}/{@code hashCode}/{@code toString} are generated from all
 *       components, so they can neither drift apart nor forget a field.</li>
 *   <li><b>"Change" means "new object".</b> {@link #withLogin(String)} returns a new instance; the
 *       original stays untouched, so an entry already stored in a map stays reachable.</li>
 *   <li><b>Validation in the compact constructor.</b> The invariant "login is not blank" is checked
 *       once at creation; since the object can never change, it holds forever.</li>
 * </ul>
 *
 * @param login user login, must not be {@code null} or blank
 * @see com.study.principles.invariants.hashkey.bad.TestUser the broken version
 */
public record TestUser(String login) {

    /**
     * Compact constructor: validates the invariant before the fields are assigned.
     *
     * @throws NullPointerException     if {@code login} is {@code null}
     * @throws IllegalArgumentException if {@code login} is blank
     */
    public TestUser {
        Objects.requireNonNull(login, "login");
        if (login.isBlank()) {
            throw new IllegalArgumentException("login must not be blank");
        }
    }

    /**
     * "Wither" instead of a setter.
     *
     * @param newLogin new login
     * @return a new {@code TestUser}; {@code this} is not modified
     */
    public TestUser withLogin(String newLogin) {
        return new TestUser(newLogin);
    }
}
