package com.study.principles.ocp;

import com.study.principles.ocp.bad.DriverFactory;
import com.study.principles.ocp.bad.EnvConfig;
import com.study.principles.ocp.bad.TimeoutSettings;
import com.study.principles.ocp.bad.ViewportSettings;
import com.study.principles.ocp.good.BrowserProfile;
import com.study.principles.ocp.good.BrowserProfiles;
import com.study.principles.ocp.good.ChromeProfile;
import com.study.principles.ocp.good.Environment;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OcpTest {

    /** Characterization: these pass <i>because</i> the bugs are there. */
    @Nested
    class Bad {

        @Test
        void remoteWasAddedToTheFactory() {
            assertThat(DriverFactory.create("remote").endpoint()).isEqualTo("http://grid:4444");
        }

        @Test
        void butTheTimeoutSwitchWasMissedAndRemoteSilentlyGetsChromeValues() {
            assertThat(TimeoutSettings.forBrowser("remote")).isEqualTo(TimeoutSettings.forBrowser("chrome"));
        }

        @Test
        void andTheViewportSwitchFailsAtRunTimeInWhicheverTestAsksFirst() {
            assertThatThrownBy(() -> ViewportSettings.forBrowser("remote"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("unknown browser: remote");
        }

        @Test
        void aNewEnvironmentIsOpenToDestructiveTestsBecauseTheCheckIsNotProd() {
            assertThat(EnvConfig.baseUrl("preprod")).isEqualTo("https://preprod.shop.test");
            assertThat(EnvConfig.destructiveAllowed("preprod")).isTrue();
        }
    }

    @Nested
    class Good {

        /** Contract for every profile, including ones added later: the test suite is open for extension too. */
        @ParameterizedTest(name = "{0}")
        @MethodSource("com.study.principles.ocp.OcpTest#profiles")
        void everyProfileAnswersEveryQuestion(BrowserProfile profile) {
            Browser browser = profile.launch();

            assertThat(profile.name()).isNotBlank();
            assertThat(profile.timeouts().navigation()).isPositive();
            assertThat(profile.timeouts().action()).isPositive().isLessThanOrEqualTo(profile.timeouts().navigation());
            assertThat(browser.viewport()).isEqualTo(profile.viewport());
        }

        @Test
        void remoteComesFromServiceLoaderWithoutEditingMainCode() {
            BrowserProfile remote = BrowserProfiles.discover().get("remote");

            assertThat(remote).isInstanceOf(RemoteGridProfile.class);
            assertThat(remote.timeouts().navigation()).isEqualTo(Duration.ofSeconds(60));
            assertThat(BrowserProfiles.builtIn().all()).extracting(BrowserProfile::name).doesNotContain("remote");
        }

        @Test
        void anUnknownNameFailsWithTheListOfWhatExists() {
            assertThatThrownBy(() -> BrowserProfiles.builtIn().get("edge"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("unknown browser 'edge', available: [chrome, firefox, mobile-web, webkit]");
        }

        @Test
        void twoProfilesWithOneNameFailOnConstruction() {
            assertThatThrownBy(() -> new BrowserProfiles(List.of(new ChromeProfile(), new ChromeProfile())))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("two profiles named 'chrome'");
        }

        @Test
        void environmentsAreDataAndANewOneIsProtectedByDefault() {
            assertThat(Environment.load("qa").destructiveAllowed()).isTrue();
            assertThat(Environment.load("prod").destructiveAllowed()).isFalse();

            Environment preprod = Environment.load("preprod");   // a file in test resources, no code
            assertThat(preprod.baseUrl()).isEqualTo("https://preprod.shop.test");
            assertThat(preprod.destructiveAllowed()).isFalse();
        }

        @Test
        void anUnknownEnvironmentNamesTheMissingFile() {
            assertThatThrownBy(() -> Environment.load("dev"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ocp/env/dev.properties");
        }
    }

    static Collection<BrowserProfile> profiles() {
        return BrowserProfiles.discover().all();
    }
}
