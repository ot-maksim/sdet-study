package com.study.framework.api.users;

import com.study.framework.core.data.Unique;

/**
 * Valid requests with values nobody cares about, unique per call so tests never collide on a shared stand.
 * A test changes the one field it is about: {@code newUsers.valid().withAge(17)}.
 */
public final class NewUsers {

    private final Unique unique;

    public NewUsers(Unique unique) {
        this.unique = unique;
    }

    public NewUser valid() {
        return new NewUser(unique.name("Ada"), unique.email("ada"), 30, "DE");
    }
}
