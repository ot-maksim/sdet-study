package com.study.project.ui.users;

/** The rejection shown under the form: the stable code ({@code data-error-code}) and the text a person reads. */
public record FormError(String code, String text) {
}
