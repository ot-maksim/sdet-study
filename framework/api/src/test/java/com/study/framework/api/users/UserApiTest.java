package com.study.framework.api.users;

import static com.study.framework.api.ApiResponseAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.study.framework.api.ApiClient;
import com.study.framework.api.ApiException;
import com.study.framework.api.ApiResponse;
import com.study.framework.api.InProcessSut;
import com.study.framework.core.data.Unique;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** One SUT for the class (like a stand for a run); tests stay independent through unique data, not a reset. */
class UserApiTest {

    private static InProcessSut sut;
    private static UserApi users;
    private final NewUsers newUsers = new NewUsers(new Unique());

    @BeforeAll
    static void start() {
        sut = new InProcessSut();
        users = new UserApi(new ApiClient(sut.settings()));
    }

    @AfterAll
    static void stop() {
        sut.close();
    }

    @Test
    void createReturnsTheNormalizedUserThatAReadReturnsToo() {
        NewUser request = newUsers.valid().withEmail(newUsers.valid().email().toUpperCase()).withCountry("gb");

        User created = users.create(request);

        assertThat(created.email()).isEqualTo(request.email().toLowerCase());
        assertThat(created.country()).isEqualTo("GB");
        assertThat(users.get(created.id())).isEqualTo(created);
        assertThat(users.findByEmail(request.email())).contains(created);
    }

    @Test
    void negativeCasesReadAsTheRule() {
        assertThat(users.tryCreate(newUsers.valid().withAge(17))).isRejectedWith(400, "age_too_low");
        assertThat(users.tryCreate(newUsers.valid().withAge(null))).isRejectedWith(400, "age_missing");
        assertThat(users.tryCreate(newUsers.valid().withCountry("XX"))).isRejectedWith(400, "country_invalid");
        assertThat(users.tryCreateRaw("{not json")).isRejectedWith(400, "malformed_json");
    }

    @Test
    void duplicateEmailIsAConflict() {
        NewUser ada = newUsers.valid();
        users.create(ada);

        assertThat(users.tryCreate(newUsers.valid().withEmail(ada.email()))).isRejectedWith(409, "email_taken");
    }

    @Test
    void happyPathFailsWithTheWholeExchange() {
        NewUser kid = newUsers.valid().withAge(17);

        assertThatThrownBy(() -> users.create(kid))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Expected 201 but got:")
                .hasMessageContaining("POST /api/users {\"name\":\"" + kid.name())
                .hasMessageContaining("-> 400 {\"error\":\"age_too_low\"");
    }

    @Test
    void assertionFailureShowsTheExchange() {
        ApiResponse adult = users.tryCreate(newUsers.valid().withAge(18));

        assertThatThrownBy(() -> assertThat(adult).isRejectedWith(400, "age_too_low"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected 400 age_too_low but got:")
                .hasMessageContaining("-> 201 {\"id\":");
    }

    @Test
    void absentIsOnlyTheSutsOwnNotFound() {
        User user = users.create(newUsers.valid());

        users.delete(user.id());

        assertThat(users.find(user.id())).isEmpty();
        assertThat(users.findByEmail(user.email())).isEmpty();
        assertThat(users.deleteIfExists(user.id())).isFalse();
        assertThatThrownBy(() -> users.delete(user.id())).hasMessageContaining("-> 404");
    }
}
