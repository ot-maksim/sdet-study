package com.study.principles.lsp.fakes;

/** What the real repository throws when the DB unique constraint on {@code email} fires. */
public final class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String email) {
        super("e-mail already registered: " + email);
    }
}
