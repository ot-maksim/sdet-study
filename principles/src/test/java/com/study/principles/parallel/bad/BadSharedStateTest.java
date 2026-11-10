package com.study.principles.parallel.bad;

import com.study.principles.parallel.Credentials;
import com.study.principles.parallel.TwoThreads;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Characterization tests: they pass <i>because</i> they pin the bugs of {@link BaseShopTest}.
 * {@code testA} and {@code testB} are two test instances of the same suite (JUnit creates a new instance
 * per test method; the static fields are what they share). {@link TwoThreads} fixes the interleaving that
 * a parallel run produces only now and then.
 */
class BadSharedStateTest {

    private final BaseShopTest testA = new BaseShopTest() { };
    private final BaseShopTest testB = new BaseShopTest() { };

    @BeforeEach
    void startSuite() {
        BaseShopTest.startClients(); // what JUnit runs as @BeforeAll
    }

    @AfterEach
    void stopSuite() {
        BaseShopTest.stopClients();
    }

    @Test
    void sharedSpec_lastAuthorizeWins_soTestASendsTestBsToken() {
        try (var threads = new TwoThreads()) {
            Credentials alice = threads.onA(testA::createUser);
            Credentials bob = threads.onB(testB::createUser);

            threads.onA(() -> BaseShopTest.authorizeAs(alice));
            threads.onB(() -> BaseShopTest.authorizeAs(bob));   // overwrites the auth of the one shared spec

            assertThat(threads.onA(testA::whoAmI)).isEqualTo(bob.username());
        }
    }

    /** No threads at all: the order of tests is enough. */
    @Test
    void sharedSpec_headersAndAuthLeakIntoLaterTests_evenSequentially() {
        // 1st test: checks the German greeting
        Credentials alice = testA.createUser();
        BaseShopTest.authorizeAs(alice);
        BaseShopTest.useLanguage("de");
        assertThat(testA.greetingViaApi()).isEqualTo("Hallo, " + alice.username());

        // 2nd test: "anonymous user gets 401". It never logged in, yet it is alice
        assertThat(testB.meStatus()).isEqualTo(200);

        // 3rd test: asks for English explicitly. header(..) appended, the server takes the first value
        BaseShopTest.useLanguage("en");
        assertThat(testB.greetingViaApi()).isEqualTo("Hallo, " + alice.username());
    }

    @Test
    void sharedDriver_showsAnotherTestsSession() {
        try (var threads = new TwoThreads()) {
            Credentials alice = threads.onA(testA::createUser);
            Credentials bob = threads.onB(testB::createUser);

            threads.onA(() -> testA.openProfileAs(alice));
            threads.onB(() -> testB.openProfileAs(bob));       // same browser: new cookie, new page

            assertThat(threads.onA(testA::greetingOnPage)).isEqualTo("Hello, " + bob.username());
        }
    }

    @Test
    void sharedDriver_oneTestsQuitKillsTheOthersBrowser() {
        try (var threads = new TwoThreads()) {
            Credentials alice = threads.onA(testA::createUser);
            threads.onA(() -> testA.openProfileAs(alice));

            threads.onB(() -> BaseShopTest.driver.quit());    // test B's cleanup in @AfterEach

            assertThatThrownBy(() -> threads.onA(testA::currentUrl))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("quit");
            BaseShopTest.startClients(); // so that stopClients() can run
        }
    }
}
