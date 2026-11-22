package com.study.project.tests;

import static org.assertj.core.api.Assertions.assertThat;

import com.study.project.api.users.User;
import com.study.project.api.users.UserApi;
import com.study.project.assembly.Groups;
import com.study.project.domain.UserSteps;
import com.study.project.events.users.UserEvents;
import com.study.project.ui.users.ShownUser;
import com.study.project.ui.users.UsersScreen;
import org.testng.annotations.Test;

/**
 * The fourth channel: what the API returned is what the SUT announced on Kafka. Runs in parallel with the rest of
 * the suite, so the topic is full of other tests' users — {@link UserEvents} finds each one by its id.
 */
@Test(groups = Groups.EVENTS)
public class UserEventsE2eTest {

    @Test
    public void userCreatedThroughApiIsAnnounced(UserSteps users, UserEvents events) {
        User ada = users.registerThroughApi(users.aNewUser());

        // The 201 does not wait for the broker (fire-and-forget): created() waits up to project.events.timeout
        assertThat(events.created(ada.id())).as("user.created for %s", ada.id()).contains(ada);
    }

    @Test
    public void userCreatedThroughPageIsAnnouncedToo(UserSteps users, UserApi api, UsersScreen screen, UserEvents events) {
        ShownUser shown = users.registerThroughUi(screen.open(), users.aNewUser());

        assertThat(events.created(shown.id())).as("user.created for %s", shown.id()).contains(api.get(shown.id()));
    }

    @Test
    public void eventsAreFoundInAnyOrder(UserSteps users, UserEvents events) {
        User ada = users.registerThroughApi(users.aNewUser());
        User bob = users.registerThroughApi(users.aNewUser());

        // Bob first: Ada's event, read while waiting for Bob's, is kept, not dropped as noise
        assertThat(events.created(bob.id())).contains(bob);
        assertThat(events.created(ada.id())).contains(ada);
        assertThat(events.seenFor(ada.id())).as("one event per creation").hasSize(1);
    }
}
