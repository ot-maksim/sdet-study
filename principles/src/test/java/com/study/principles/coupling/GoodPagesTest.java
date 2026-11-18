package com.study.principles.coupling;

import com.study.principles.coupling.good.Browsers;
import com.study.principles.coupling.good.DashboardPage;
import com.study.principles.coupling.good.LoginPage;
import com.study.principles.coupling.good.Pages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain unit tests: with constructor injection a page is just {@code new LoginPage(anyBrowser)}. */
@DisplayName("good: injected browser, explicit ownership")
class GoodPagesTest {

    @Test
    void pagesOverOneBrowserShareTheSession() {
        try (Browser browser = new FakeBrowser()) {
            new LoginPage(browser).open().loginAs("alice", "alice-pass");

            assertThat(new DashboardPage(browser).open().greeting()).isEqualTo("Hello, alice");
        }
    }

    @Test
    void anyBrowserImplementationFits() {
        Browser recording = new RecordingBrowser(new FakeBrowser());

        new Pages(recording).login().open().loginAs("alice", "wrong");

        assertThat(recording.toString()).contains("open /login", "click sign-in");
    }

    @Test
    void theDependencyIsCheckedAtConstructionNotOnTheFirstClick() {
        assertThatThrownBy(() -> new LoginPage(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("browser");
    }

    @Test
    void factoryGivesIndependentSessionsAndClosesAllOfThem() {
        Browsers browsers = new Browsers(FakeBrowser::new);
        try (browsers) {
            Pages alice = browsers.newSession();
            Pages anonymous = browsers.newSession();
            alice.login().open().loginAs("alice", "alice-pass");

            assertThat(anonymous.dashboard().open()).isNotNull();
            assertThatThrownBy(() -> anonymous.dashboard().greeting()).hasMessageContaining("on /login");
            assertThat(alice.dashboard().greeting()).isEqualTo("Hello, alice");
        }

        assertThat(browsers.launched()).hasSize(2).allMatch(Browser::isClosed);
    }

    @Test
    void theLauncherIsTheOnlyPlaceThatKnowsTheBrowserKind() {
        Supplier<Browser> recordingLauncher = () -> new RecordingBrowser(new FakeBrowser());

        try (Browsers browsers = new Browsers(recordingLauncher)) {
            browsers.newSession().login().open();

            assertThat(browsers.launched().get(0)).isInstanceOf(RecordingBrowser.class);
        }
    }

    /** A decorator — the kind of thing a team adds later (tracing, screenshots) without touching pages. */
    private static final class RecordingBrowser implements Browser {
        private final Browser delegate;
        private final StringBuilder log = new StringBuilder();

        RecordingBrowser(Browser delegate) {
            this.delegate = delegate;
        }

        @Override
        public void open(String path) {
            log.append("open ").append(path).append("; ");
            delegate.open(path);
        }

        @Override
        public void fill(String field, String value) {
            log.append("fill ").append(field).append("; ");
            delegate.fill(field, value);
        }

        @Override
        public void click(String button) {
            log.append("click ").append(button).append("; ");
            delegate.click(button);
        }

        @Override
        public String currentPath() {
            return delegate.currentPath();
        }

        @Override
        public String text(String id) {
            return delegate.text(id);
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public String toString() {
            return log.toString();
        }
    }
}
