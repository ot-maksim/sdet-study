package com.study.sut.users;

import java.util.Locale;
import java.util.Set;

/**
 * Validation and normalization of a {@link NewUser}. These rules are the SUT's business logic that
 * tests check from the outside; the first failing rule wins, in field order.
 *
 * <ul>
 *   <li>name: trimmed, not blank ({@code name_blank}), at most 100 chars ({@code name_too_long})</li>
 *   <li>email: trimmed and lower-cased, {@code local@domain.tld}, at most 254 chars ({@code email_invalid})</li>
 *   <li>age: present ({@code age_missing}), 18..150 ({@code age_too_low}, {@code age_too_high})</li>
 *   <li>country: ISO 3166 alpha-2, upper-cased ({@code country_invalid})</li>
 * </ul>
 */
public final class UserRules {

    public static final int MIN_AGE = 18;
    public static final int MAX_AGE = 150;
    public static final int MAX_NAME = 100;
    public static final int MAX_EMAIL = 254;

    private static final Set<String> COUNTRIES = Set.of(Locale.getISOCountries());

    private UserRules() {
    }

    public static ValidUser validate(NewUser user) {
        String name = user.name() == null ? "" : user.name().strip();
        if (name.isEmpty()) {
            throw UserRejectedException.invalid("name_blank", "Name must not be blank");
        }
        if (name.length() > MAX_NAME) {
            throw UserRejectedException.invalid("name_too_long", "Name must be at most " + MAX_NAME + " characters");
        }

        String email = user.email() == null ? "" : user.email().strip().toLowerCase(Locale.ROOT);
        if (email.length() > MAX_EMAIL || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw UserRejectedException.invalid("email_invalid", "Email is not valid: " + user.email());
        }

        if (user.age() == null) {
            throw UserRejectedException.invalid("age_missing", "Age is required");
        }
        if (user.age() < MIN_AGE) {
            throw UserRejectedException.invalid("age_too_low", "Age must be at least " + MIN_AGE);
        }
        if (user.age() > MAX_AGE) {
            throw UserRejectedException.invalid("age_too_high", "Age must be at most " + MAX_AGE);
        }

        String country = user.country() == null ? "" : user.country().strip().toUpperCase(Locale.ROOT);
        if (!COUNTRIES.contains(country)) {
            throw UserRejectedException.invalid("country_invalid", "Country must be an ISO 3166 alpha-2 code: " + user.country());
        }

        return new ValidUser(name, email, user.age(), country);
    }
}
