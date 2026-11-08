package com.study.principles.records.traps.good;

import java.util.Objects;

/**
 * GOOD: still a record, but {@code toString} is overridden to mask the secret.
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li><b>Override only what is wrong.</b> {@code equals}/{@code hashCode} over both components are
 *       still correct, so they stay generated.</li>
 *   <li><b>Fixed mask, not "first two characters".</b> Any part of a password, or its length, is a hint.</li>
 *   <li>The accessor {@code password()} is still public — a record cannot hide a component. That is fine
 *       for test credentials that the login step must read; for a token that only the client should use,
 *       a record is the wrong tool (see {@code misfit}).</li>
 * </ul>
 *
 * @param login    login
 * @param password password, never printed
 * @see com.study.principles.records.traps.bad.Credentials the leaking version
 */
public record Credentials(String login, String password) {

    public Credentials {
        Objects.requireNonNull(login, "login");
        Objects.requireNonNull(password, "password");
    }

    @Override
    public String toString() {
        return "Credentials[login=" + login + ", password=***]";
    }
}
