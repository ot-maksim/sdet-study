package com.study.principles.deep;

import com.study.principles.deep.bad.HttpHelper;
import com.study.principles.deep.bad.UserSteps;
import com.study.principles.deep.good.AuthApi;
import com.study.principles.deep.good.NewUser;
import com.study.principles.deep.good.User;
import com.study.principles.deep.good.UserApi;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Assume;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.NotBlank;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Any name survives a round trip." This property can be stated only because serialization lives in one
 * deep module; with {@code String.format} in every step there is no single place to state it about.
 */
class UserApiPropertyTest {

    @Property(tries = 300)
    void anyNameRoundTripsThroughTheDeepApi(@ForAll @NotBlank String name) {
        // jqwik's @NotBlank only trims chars <= ' '; the SUT uses String.isBlank(), which also rejects
        // Unicode spaces such as U+2003. The SUT's definition of "blank" wins — jqwik found the mismatch.
        Assume.that(!name.isBlank());
        UserApi users = new UserApi(new AuthApi(new FakeUsersBackend())
                .login(FakeUsersBackend.ADMIN, FakeUsersBackend.ADMIN_PASSWORD));

        User created = users.create(NewUser.user(name, "u@test.io"));

        assertThat(users.get(created.id()).name()).isEqualTo(name);
    }

    /** Characterization: for names that need escaping, the shallow steps either fail or store a different name. */
    @Property(tries = 300)
    void shallowStepsNeverRoundTripANameThatNeedsEscaping(@ForAll String prefix,
                                                           @ForAll("needsEscaping") char special,
                                                           @ForAll String suffix) {
        String name = prefix + special + suffix;
        UserSteps steps = new UserSteps(new HttpHelper(new FakeUsersBackend()));
        steps.loginAsAdmin();

        String stored;
        try {
            steps.createUser(name, "u@test.io", "USER");
            stored = Json.field(steps.getResponseBody(), "name");   // e.g. "a\nb" typed as 4 chars comes back as 3
        } catch (NumberFormatException malformedJsonOrRejected) {
            stored = null;
        }

        assertThat(stored).isNotEqualTo(name);
    }

    @Provide
    Arbitrary<Character> needsEscaping() {
        return Arbitraries.of('"', '\\', '\n', '\t');
    }
}
