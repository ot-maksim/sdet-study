package com.study.principles.inheritance.session;

import com.study.principles.inheritance.session.bad.MobileSession;
import com.study.principles.inheritance.session.bad.WebSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Characterization tests: they PASS because they pin down the bugs of the bad design.
 * Exercise: "fix" bad.* one by one and watch these tests go red — then notice
 * you had to fix the same thing in every subclass.
 */
@DisplayName("bad: each subclass breaks the lifecycle in its own way")
class BadSessionTest {

    @Test
    void webSession_doubleCloseThrows() {
        var s = new WebSession(new BrowserFarm());
        s.start();
        s.close();
        assertThatThrownBy(s::close).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void mobileSession_doubleCloseIsFine_rulesDifferBetweenSubclasses() {
        var s = new MobileSession(new BrowserFarm());
        s.start();
        s.close();
        s.close();
        assertThat(s.isClosed()).isTrue();
    }

    @Test
    void failedStart_leaksBrowser() {
        var farm = new BrowserFarm().failOnNewPage();
        assertThatThrownBy(() -> new WebSession(farm).start()).isInstanceOf(RuntimeException.class);
        assertThat(farm.openBrowsers()).isEqualTo(1);
    }

    @Test
    void startTwice_leaksFirstBrowser() {
        var farm = new BrowserFarm();
        var s = new MobileSession(farm);
        s.start();
        s.start();
        s.close();
        assertThat(farm.openBrowsers()).isEqualTo(1);
    }

    @Test
    void useAfterClose_silentlyReturnsDeadPage() {
        var s = new MobileSession(new BrowserFarm());
        s.start();
        s.close();
        assertThat(s.page().isAlive()).isFalse();
    }

    @Test
    void subclassCanCorruptProtectedState() {
        var s = new WebSession(new BrowserFarm()) {
            @Override
            public void start() {
                closed = true;              // nothing stops a subclass from doing this
                super.start();
            }
        };
        s.start();
        assertThat(s.isClosed()).isTrue();
        assertThat(s.page()).isNotNull();   // "closed" and usable at the same time
    }
}
