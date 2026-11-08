package com.study.principles.records.fit.bad;

import java.util.Objects;

/**
 * BAD: a hand-written DTO for {@code GET /users/{id}}.
 *
 * <h2>Problem: equals/hashCode/toString drift away from the fields</h2>
 * The class started with {@code id, login, email}. Later the API began returning {@code role}; the field,
 * getter and setter were added, but {@code equals}, {@code hashCode} and {@code toString} were not
 * updated. Nothing fails to compile, and the consequence lands in the tests:
 * <pre>{@code
 * assertThat(actual).isEqualTo(expectedAdmin);   // passes for a USER — a false positive
 * }</pre>
 * Additional noise: a no-arg constructor plus setters (for "the JSON library"), so a half-filled object is
 * a legal state, and an expected value can be changed after it was put into a {@code Set}.
 *
 * @see com.study.principles.records.fit.good.UserResponse the record version
 */
public class UserResponse {
    private long id;
    private String login;
    private String email;
    private String role;    // added later — equals/hashCode/toString were not updated

    public UserResponse() {
    }

    public UserResponse(long id, String login, String email, String role) {
        this.id = id;
        this.login = login;
        this.email = email;
        this.role = role;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserResponse that)) return false;
        return id == that.id && Objects.equals(login, that.login) && Objects.equals(email, that.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, login, email);
    }

    @Override
    public String toString() {
        return "UserResponse{id=" + id + ", login=" + login + ", email=" + email + "}";
    }
}
