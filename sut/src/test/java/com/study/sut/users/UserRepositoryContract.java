package com.study.sut.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * What every {@link UserRepository} promises; implemented as a JUnit test interface
 * (see principles/lsp: a fake is a subtype too and must keep the same contract).
 * Emails are unique per test so the contract also runs against a shared database.
 */
public interface UserRepositoryContract {

    Instant CREATED = Instant.parse("2026-11-19T10:15:30.123456Z");

    UserRepository repository();

    private static ValidUser aUser() {
        return new ValidUser("Ada", "ada-" + UUID.randomUUID() + "@example.com", 36, "GB");
    }

    @Test
    default void insertAssignsIdAndReturnsWhatWasStored() {
        ValidUser user = aUser();

        User stored = repository().insert(user, CREATED);

        assertThat(stored.id()).isPositive();
        assertThat(stored).isEqualTo(new User(stored.id(), "Ada", user.email(), 36, "GB", CREATED));
        assertThat(repository().findById(stored.id())).contains(stored);
        assertThat(repository().findByEmail(user.email())).contains(stored);
    }

    @Test
    default void emailIsUnique() {
        ValidUser user = aUser();
        repository().insert(user, CREATED);

        assertThatThrownBy(() -> repository().insert(user, CREATED))
                .isInstanceOfSatisfying(UserRejectedException.class, e -> assertThat(e.code()).isEqualTo("email_taken"));
    }

    @Test
    default void idsAreNotReusedAndListIsOrderedById() {
        User first = repository().insert(aUser(), CREATED);
        assertThat(repository().delete(first.id())).isTrue();
        User second = repository().insert(aUser(), CREATED);
        User third = repository().insert(aUser(), CREATED);

        assertThat(second.id()).isGreaterThan(first.id());
        assertThat(repository().findAll()).containsSubsequence(second, third).doesNotContain(first);
    }

    @Test
    default void deleteOfUnknownIdReportsFalse() {
        assertThat(repository().delete(Long.MAX_VALUE)).isFalse();
        assertThat(repository().findById(Long.MAX_VALUE)).isEmpty();
    }
}
