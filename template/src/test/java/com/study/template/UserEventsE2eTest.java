package com.study.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.study.framework.api.users.User;
import com.study.framework.api.users.UserApi;
import com.study.framework.domain.UserSteps;
import com.study.framework.ui.users.ShownUser;
import com.study.framework.ui.users.UsersScreen;
import com.study.template.events.users.UserEvents;
import org.junit.jupiter.api.Test;

/**
 * The fourth channel: what the API returned is what the SUT announced on Kafka. Runs in parallel with the rest of
 * the suite, so the topic is full of other tests' users — {@link UserEvents} finds each one by its id.
 */
@EventTests
class UserEventsE2eTest {

    @Test
    void userCreatedThroughApiIsAnnounced(UserSteps users, UserEvents events) {
        User ada = users.registerThroughApi(users.aNewUser());

        // The 201 does not wait for the broker (fire-and-forget): created() waits up to template.events.timeout
        assertThat(events.created(ada.id())).as("user.created for %s", ada.id()).contains(ada);
    }

    @Test
    void userCreatedThroughPageIsAnnouncedToo(UserSteps users, UserApi api, UsersScreen screen, UserEvents events) {
        ShownUser shown = users.registerThroughUi(screen.open(), users.aNewUser());

        assertThat(events.created(shown.id())).as("user.created for %s", shown.id()).contains(api.get(shown.id()));
    }

    @Test
    void eventsAreFoundInAnyOrder(UserSteps users, UserEvents events) {
        User ada = users.registerThroughApi(users.aNewUser());
        User bob = users.registerThroughApi(users.aNewUser());

        // Bob first: Ada's event, read while waiting for Bob's, is kept, not dropped as noise
        assertThat(events.created(bob.id())).contains(bob);
        assertThat(events.created(ada.id())).contains(ada);
        assertThat(events.seenFor(ada.id())).as("one event per creation").hasSize(1);
    }
}
