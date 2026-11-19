package com.study.framework.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.study.framework.api.users.NewUser;
import com.study.framework.api.users.NewUsers;
import com.study.framework.api.users.User;
import com.study.framework.api.users.UserApi;
import com.study.framework.core.lifecycle.Cleanup;
import com.study.framework.core.lifecycle.Scope;
import org.junit.jupiter.api.Test;

/**
 * Every {@link UserPrecondition} must produce users indistinguishable, through the API, from users the API
 * created. For {@link DbUserPrecondition} this is what catches its copy of the SUT's normalization drifting.
 */
public interface UserPreconditionContract {

    UserPrecondition precondition(Cleanup cleanup);

    @Test
    default void userLooksAsIfTheApiHadCreatedIt(UserApi api, NewUsers newUsers, Cleanup cleanup) {
        NewUser messy = newUsers.valid();
        messy = messy.withName("  " + messy.name() + "  ").withEmail(messy.email().toUpperCase()).withCountry("gb");
        NewUser messyTwin = newUsers.valid();
        messyTwin = messyTwin.withName(messy.name()).withEmail(messyTwin.email().toUpperCase()).withCountry("gb");

        User seeded = precondition(cleanup).create(messy);
        User viaApi = api.create(messyTwin);
        cleanup.defer("delete " + viaApi.id(), () -> api.deleteIfExists(viaApi.id()));

        assertThat(api.get(seeded.id())).isEqualTo(seeded);
        assertThat(seeded).usingRecursiveComparison().ignoringFields("id", "email", "createdAt").isEqualTo(viaApi);
        assertThat(seeded.email()).isEqualTo(messy.email().toLowerCase());
    }

    @Test
    default void userIsRemovedAfterTheTest(UserApi api, NewUsers newUsers) {
        Scope testScope = new Scope("simulated test");
        User user = precondition(testScope).create(newUsers.valid());
        assertThat(api.find(user.id())).isPresent();

        testScope.close();

        assertThat(api.find(user.id())).isEmpty();
    }
}
