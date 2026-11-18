package com.study.principles.demeter;

import com.study.principles.demeter.FakeApp.Layout;
import com.study.principles.demeter.bad.UserScenarios;
import com.study.principles.demeter.good.AppPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The redesign moves the user menu from the header to the sidebar. Train wrecks encode the path in every
 * test and all of them break; intention methods keep the path in one private method.
 */
@DisplayName("Law of Demeter: the V2 redesign")
class RedesignTest {

    @Nested
    @DisplayName("bad: train wrecks in every scenario")
    class Bad {

        private static Stream<Consumer<com.study.principles.demeter.bad.AppPage>> scenarios() {
            return Stream.of(UserScenarios::logout, page -> UserScenarios.switchLanguage(page, "de"),
                    UserScenarios::openProfile);
        }

        @Test
        void allScenariosWorkOnTheLayoutTheyWereWrittenFor() {
            scenarios().forEach(scenario -> {
                FakeApp app = new FakeApp(Layout.V1);
                scenario.accept(new com.study.principles.demeter.bad.AppPage(app));
                assertThat(app.path().equals("/home") && app.language().equals("en") && app.loggedIn())
                        .as("the scenario changed something").isFalse();
            });
        }

        @Test
        void everyScenarioBreaksAfterTheRedesign() {
            assertThat(scenarios()).allSatisfy(scenario ->
                    assertThatThrownBy(() -> scenario.accept(new com.study.principles.demeter.bad.AppPage(new FakeApp(Layout.V2))))
                            .isInstanceOf(NoSuchElementException.class)
                            .hasMessageContaining("header > user-menu"));
        }

        @Test
        void theStructureAlsoLeaksStepOrderIntoTests() {
            var page = new com.study.principles.demeter.bad.AppPage(new FakeApp(Layout.V1));

            // the dropdown is reachable without open(): the API allows a sequence the UI does not
            assertThatThrownBy(() -> page.getHeader().getUserMenu().getDropdown().getItem("Logout").click())
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested
    @DisplayName("good: intention methods")
    class Good {

        @ParameterizedTest
        @EnumSource(Layout.class)
        void logoutWorksOnAnyLayout(Layout layout) {
            FakeApp app = new FakeApp(layout);

            new AppPage(app).logout();

            assertThat(app.loggedIn()).isFalse();
            assertThat(app.path()).isEqualTo("/login");
        }

        @ParameterizedTest
        @EnumSource(Layout.class)
        void switchLanguageWorksOnAnyLayout(Layout layout) {
            FakeApp app = new FakeApp(layout);

            new AppPage(app).switchLanguage("de");

            assertThat(app.language()).isEqualTo("de");
        }

        @ParameterizedTest
        @EnumSource(Layout.class)
        void openProfileWorksOnAnyLayout(Layout layout) {
            FakeApp app = new FakeApp(layout);

            new AppPage(app).openProfile();

            assertThat(app.path()).isEqualTo("/profile");
        }

        @Test
        void testsCannotReachTheComponentsAtAll() {
            assertThat(AppPage.class.getMethods())
                    .filteredOn(m -> m.getDeclaringClass() == AppPage.class)
                    .allSatisfy(m -> assertThat(m.getReturnType()).isEqualTo(void.class));
        }
    }
}
