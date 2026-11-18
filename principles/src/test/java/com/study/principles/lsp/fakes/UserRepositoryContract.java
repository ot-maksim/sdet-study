package com.study.principles.lsp.fakes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The {@link UserRepository} contract, run against the real implementation <i>and</i> every fake. A fake that
 * passes is substitutable: a test green on it means the same on the real database. In stage 2 the same
 * interface runs against the JDBC repository on Postgres.
 */
public interface UserRepositoryContract {

    UserRepository repository();

    @Test
    default void insertAssignsDistinctIds() {
        UserRepository users = repository();

        User alice = users.insert(new User("alice@shop.test", "Alice"));
        User bob = users.insert(new User("bob@shop.test", "Bob"));

        assertThat(alice.id()).isNotNull().isNotEqualTo(bob.id());
    }

    @Test
    default void aSecondUserWithTheSameEmailIsRejected() {
        UserRepository users = repository();
        users.insert(new User("alice@shop.test", "Alice"));

        assertThatThrownBy(() -> users.insert(new User("alice@shop.test", "Another Alice")))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    default void returnedObjectsAreDetachedCopies() {
        UserRepository users = repository();
        users.insert(new User("alice@shop.test", "Alice"));

        users.findByEmail("alice@shop.test").orElseThrow().rename("Changed without update");

        assertThat(users.findByEmail("alice@shop.test").orElseThrow().name()).isEqualTo("Alice");
    }

    @Test
    default void updateStoresTheCurrentState() {
        UserRepository users = repository();
        User alice = users.insert(new User("alice@shop.test", "Alice"));

        alice.rename("Alice Smith");
        users.update(alice);

        assertThat(users.findByEmail("alice@shop.test").orElseThrow().name()).isEqualTo("Alice Smith");
    }
}
