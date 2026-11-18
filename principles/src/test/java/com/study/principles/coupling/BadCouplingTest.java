package com.study.principles.coupling;

import com.study.principles.coupling.bad.captive.LoginSteps;
import com.study.principles.coupling.bad.locator.DriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Characterization tests: they PASS because they pin down what each coupled design does. */
@DisplayName("bad: the dependency is created inside, looked up globally, or held too long")
class BadCouplingTest {

    @Nested
    @DisplayName("new inside the page")
    class NewInside {

        private final com.study.principles.coupling.bad.newinside.LoginPage login =
                new com.study.principles.coupling.bad.newinside.LoginPage();
        private final com.study.principles.coupling.bad.newinside.DashboardPage dashboard =
                new com.study.principles.coupling.bad.newinside.DashboardPage();

        @AfterEach
        void closeEveryPagesBrowserByHand() {
            login.getBrowser().close();       // the test has to know each page's internals to clean up
            dashboard.getBrowser().close();
        }

        @Test
        void eachPageIsASeparateBrowserSoTheLoginIsLost() {
            login.open();
            login.loginAs("alice", "alice-pass");
            dashboard.open();

            assertThat(login.getBrowser()).isNotSameAs(dashboard.getBrowser());
            assertThat(login.getBrowser().currentPath()).isEqualTo("/dashboard");
            assertThat(dashboard.getBrowser().currentPath()).isEqualTo("/login");   // redirected: anonymous
            assertThatThrownBy(dashboard::greeting).hasMessageContaining("no element 'greeting' on /login");
        }
    }

    @Nested
    @DisplayName("static DriverManager (service locator)")
    class Locator {

        @AfterEach
        void resetGlobal() {
            DriverManager.set(null);   // every test must remember this, or the next one inherits its browser
        }

        @Test
        void constructionSucceedsAndTheFirstActionFailsWithoutSayingWhatIsMissing() {
            var login = new com.study.principles.coupling.bad.locator.LoginPage();   // "needs nothing"

            assertThatThrownBy(login::open)
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("DriverManager.get()");
        }

        @Test
        void aTestInheritsTheBrowserThePreviousTestLeftInTheHolder() {
            Browser previous = new FakeBrowser();
            DriverManager.set(previous);
            previous.close();   // previous test's teardown: closed, but not cleared

            var login = new com.study.principles.coupling.bad.locator.LoginPage();

            assertThatThrownBy(login::open).hasMessageEndingWith("is closed");
        }

        @Test
        void sharingWorksOnlyWhileEverybodyReadsTheSameGlobal() {
            try (Browser browser = new FakeBrowser()) {
                DriverManager.set(browser);
                var login = new com.study.principles.coupling.bad.locator.LoginPage();
                login.open();
                login.loginAs("alice", "alice-pass");

                assertThat(new com.study.principles.coupling.bad.locator.DashboardPage().greeting())
                        .isEqualTo("Hello, alice");   // it works — the cost is invisible in signatures
            }
        }
    }

    @Nested
    @DisplayName("captive dependency")
    class Captive {

        @AfterEach
        void resetSingleton() {
            LoginSteps.reset();
        }

        @Test
        void singletonKeepsTheFirstTestsBrowserAfterItWasClosed() {
            String firstTestsBrowser;
            try (Browser test1 = new FakeBrowser()) {
                firstTestsBrowser = test1.toString();
                assertThat(LoginSteps.instance(test1).loginAs("alice", "alice-pass")).isEqualTo("Hello, alice");
            }

            try (Browser test2 = new FakeBrowser()) {
                assertThatThrownBy(() -> LoginSteps.instance(test2).loginAs("bob", "bob-pass"))
                        .hasMessage(firstTestsBrowser + " is closed");
                assertThat(test2.currentPath()).isEqualTo("about:blank");   // the browser it passed in: never used
            }
        }
    }
}
