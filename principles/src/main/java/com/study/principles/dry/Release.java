package com.study.principles.dry;

/**
 * A build of the system under test. The kata deploys broken builds to see which tests notice.
 *
 * @param minAge      the youngest age the app accepts (product rule: 18)
 * @param usersPath   where the users endpoint lives; a rename is a change every test suite meets sooner or later
 * @param keepsCountry {@code false} simulates a bug: the app ignores the posted country and stores "US"
 */
public record Release(int minAge, String usersPath, boolean keepsCountry) {

    public static final Release CURRENT = new Release(18, "/api/v1/users", true);

    public static final String COMPANIES_PATH = "/api/v1/companies";

    /** A regression: someone "aligned" the age rule with another market. */
    public Release withMinAge(int minAge) {
        return new Release(minAge, usersPath, keepsCountry);
    }

    public Release withUsersPath(String usersPath) {
        return new Release(minAge, usersPath, keepsCountry);
    }

    /** A regression: the country field is dropped on the way to the database. */
    public Release losingCountry() {
        return new Release(minAge, usersPath, false);
    }
}
