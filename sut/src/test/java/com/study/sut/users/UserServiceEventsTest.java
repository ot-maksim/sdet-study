package com.study.sut.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class UserServiceEventsTest {

    private final List<User> published = new CopyOnWriteArrayList<>();
    private final UserService service = new UserService(new InMemoryUserRepository(), Clock.systemUTC(), published::add);

    @Test
    void createdUserIsPublishedAsStored() {
        User user = service.create(new NewUser("Ada", " ADA@example.com ", 36, "gb"));

        assertThat(published).containsExactly(user);
        assertThat(user.email()).isEqualTo("ada@example.com");
    }

    @Test
    void rejectedUserIsNotPublished() {
        service.create(new NewUser("Ada", "ada@example.com", 36, "GB"));

        assertThatThrownBy(() -> service.create(new NewUser("Ada", "ada@example.com", 36, "GB")))
                .isInstanceOf(UserRejectedException.class);
        assertThatThrownBy(() -> service.create(new NewUser("", "bob@example.com", 36, "GB")))
                .isInstanceOf(UserRejectedException.class);
        assertThat(published).hasSize(1);
    }
}
