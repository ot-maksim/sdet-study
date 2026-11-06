package com.study.principles.inheritance.session;

import com.study.principles.inheritance.session.composition.ManagedSession;
import com.study.principles.inheritance.session.composition.WebDriver;
import com.study.principles.inheritance.session.good.MobileSession;
import com.study.principles.inheritance.session.good.WebSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Named.named;

/**
 * One contract test for ALL correct implementations (inheritance-based and composition-based).
 * Adding a new session type = adding one line to {@link #sessions()}. This is also an LSP check:
 * every implementation must honour the same contract.
 */
@DisplayName("good + composition: lifecycle invariants")
class SessionInvariantsTest {

    record Session(Runnable start, Supplier<FakePage> page, Runnable close, Supplier<String> state) {
    }

    static Stream<Named<Function<BrowserFarm, Session>>> sessions() {
        return Stream.of(
                named("good.WebSession", farm -> {
                    var s = new WebSession(farm);
                    return new Session(s::start, s::page, s::close, () -> s.state().name());
                }),
                named("good.MobileSession", farm -> {
                    var s = new MobileSession(farm);
                    return new Session(s::start, s::page, s::close, () -> s.state().name());
                }),
                named("composition.ManagedSession", farm -> {
                    var s = new ManagedSession(new WebDriver(farm));
                    return new Session(s::start, s::page, s::close, () -> s.state().name());
                })
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sessions")
    void inv1_transitionsGoNewStartedClosed(Function<BrowserFarm, Session> create) {
        var s = create.apply(new BrowserFarm());
        assertThat(s.state().get()).isEqualTo("NEW");
        s.start().run();
        assertThat(s.state().get()).isEqualTo("STARTED");
        s.close().run();
        assertThat(s.state().get()).isEqualTo("CLOSED");
        assertThatThrownBy(s.start()::run).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sessions")
    void inv1_cannotStartTwice_noLeak(Function<BrowserFarm, Session> create) {
        var farm = new BrowserFarm();
        var s = create.apply(farm);
        s.start().run();
        assertThatThrownBy(s.start()::run).isInstanceOf(IllegalStateException.class);
        s.close().run();
        assertThat(farm.openBrowsers()).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sessions")
    void inv2_actionsOnlyWhenStarted(Function<BrowserFarm, Session> create) {
        var s = create.apply(new BrowserFarm());
        assertThatThrownBy(s.page()::get).hasMessageContaining("NEW");
        s.start().run();
        assertThat(s.page().get().isAlive()).isTrue();
        s.close().run();
        assertThatThrownBy(s.page()::get).hasMessageContaining("CLOSED");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sessions")
    void inv3_closeIsIdempotent(Function<BrowserFarm, Session> create) {
        var farm = new BrowserFarm();
        var s = create.apply(farm);
        s.start().run();
        s.close().run();
        s.close().run();
        assertThat(farm.openBrowsers()).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sessions")
    void inv3_closeWithoutStartIsSafe(Function<BrowserFarm, Session> create) {
        var s = create.apply(new BrowserFarm());
        s.close().run();
        assertThat(s.state().get()).isEqualTo("CLOSED");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sessions")
    void inv4_failedStartReleasesResources(Function<BrowserFarm, Session> create) {
        var farm = new BrowserFarm().failOnNewPage();
        var s = create.apply(farm);
        assertThatThrownBy(s.start()::run).hasMessageContaining("failed to create page");
        assertThat(farm.openBrowsers()).isZero();
        assertThat(s.state().get()).isEqualTo("CLOSED");
    }
}
