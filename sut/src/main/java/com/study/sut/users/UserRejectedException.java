package com.study.sut.users;

/**
 * A request the service refuses. {@code code} is the stable, machine-readable part of the API
 * ({@code "age_too_low"}); {@code message} is for humans and may change.
 */
public class UserRejectedException extends RuntimeException {

    public enum Reason { INVALID, CONFLICT, NOT_FOUND }

    private final Reason reason;
    private final String code;

    public UserRejectedException(Reason reason, String code, String message) {
        super(message);
        this.reason = reason;
        this.code = code;
    }

    public static UserRejectedException invalid(String code, String message) {
        return new UserRejectedException(Reason.INVALID, code, message);
    }

    public static UserRejectedException emailTaken(String email) {
        return new UserRejectedException(Reason.CONFLICT, "email_taken", "Email is already registered: " + email);
    }

    public static UserRejectedException notFound(long id) {
        return new UserRejectedException(Reason.NOT_FOUND, "user_not_found", "No user with id " + id);
    }

    public Reason reason() {
        return reason;
    }

    public String code() {
        return code;
    }
}
