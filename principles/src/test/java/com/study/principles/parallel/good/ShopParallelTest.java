package com.study.principles.parallel.good;

import com.study.principles.parallel.Credentials;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A real parallel JUnit run ({@code junit-platform.properties} enables parallelism, this class opts in).
 * Tests only declare what they need; {@link ShopExtension} decides what is shared and what is per test.
 * Run it as often as you like: it never flakes.
 */
@ExtendWith(ShopExtension.class)
@Execution(ExecutionMode.CONCURRENT)
class ShopParallelTest {

    @RepeatedTest(30)
    void newUserSeesOwnNameViaApiAndOnProfilePage(ShopApi api, ProfilePage profile, TestUsers users) {
        Credentials me = users.create();
        ShopApi asMe = api.as(api.login(me)).withLanguage("de");

        assertThat(asMe.me().greeting()).isEqualTo("Hallo, " + me.username());
        assertThat(profile.openWithSession(api.login(me)).greeting()).isEqualTo("Hello, " + me.username());
    }

    /** 2 premium accounts, many parallel tests: the pool serializes exactly these tests, nothing else. */
    @RepeatedTest(10)
    void premiumUserSeesPremiumBadge(ShopApi api, ProfilePage profile, TestUsers users) {
        Credentials premium = users.leasePremium();

        profile.openWithSession(api.login(premium));

        assertThat(profile.isOpen()).as("still logged in: nobody else used this account").isTrue();
        assertThat(profile.plan()).isEqualTo("PREMIUM");
    }

    @AfterAll
    static void sharedStateWasSharedOnlyWhereIntended(ShopEnvironment environment) {
        assertThat(environment.adminLogins()).isEqualTo(1);
        assertThat(environment.premiumAccounts().available()).isEqualTo(environment.premiumAccounts().size());
    }
}
