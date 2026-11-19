package com.study.framework.ui.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.study.framework.core.data.Unique;
import org.junit.jupiter.api.Test;

/**
 * What every {@link UsersScreen} must do, whatever drives it: web, mobile web, later a native app.
 * Implemented by one test class per way of driving the screen.
 */
public interface UsersScreenContract {

    UsersScreen screen();

    Unique unique();

    default UserForm aUser() {
        return new UserForm(unique().name("Ada"), unique().email("ada"), "30", "de");
    }

    @Test
    default void createdUserIsListedNormalized() {
        UserForm form = aUser().withEmail(unique().email("ADA"));

        ShownUser created = screen().create(form);

        assertThat(created.email()).isEqualTo(form.email().toLowerCase());
        assertThat(created.country()).isEqualTo("DE");
        assertThat(screen().notice()).contains("User created");
        assertThat(screen().find(created.email())).contains(created);
    }

    @Test
    default void rejectionShowsTheCode() {
        assertThat(screen().createRejected(aUser().withAge("17")).code()).isEqualTo("age_too_low");
        assertThat(screen().createRejected(aUser().withAge("old")).code()).isEqualTo("age_invalid");
    }

    @Test
    default void deletedUserDisappears() {
        ShownUser user = screen().create(aUser());

        screen().delete(user.email());

        assertThat(screen().find(user.email())).isEmpty();
        assertThat(screen().notice()).contains("User deleted");
    }
}
