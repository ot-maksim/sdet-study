package com.study.principles.isp;

import com.study.principles.isp.bad.Driver;
import com.study.principles.isp.bad.NativeDriverImpl;
import com.study.principles.isp.bad.WebDriverImpl;
import com.study.principles.isp.good.AppiumDevice;
import com.study.principles.isp.good.Browser;
import com.study.principles.isp.good.CookieLogin;
import com.study.principles.isp.good.Interaction;
import com.study.principles.isp.good.LoginPage;
import com.study.principles.isp.good.MobileApp;
import com.study.principles.isp.good.PlaywrightBrowser;
import com.study.principles.isp.good.ScreenshotOnFailure;
import com.study.principles.isp.good.SwipeCarousel;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Named.named;

class IspTest {

    @Nested
    class Bad {

        @Test
        void bothImplementationsAreForcedToStubWhatTheyCannotDo() {
            assertThat(unsupportedMethods(new WebDriverImpl(new FakeScreen()), Driver.class))
                    .containsExactlyInAnyOrder("tap", "swipe", "installApp", "launchApp");
            assertThat(unsupportedMethods(new NativeDriverImpl(new FakeScreen()), Driver.class))
                    .containsExactlyInAnyOrder("open", "currentUrl", "executeScript", "addCookie", "clearCookies");
        }

        @Test
        void theSharedLoginPageWorksOnWebAndFailsOnNativeAtRunTime() {
            var web = new com.study.principles.isp.bad.LoginPage(new WebDriverImpl(new FakeScreen()));
            web.open().loginAs("alice", "alice-pass");
            assertThat(web.greeting()).isEqualTo("Hello, alice");

            var phone = new com.study.principles.isp.bad.LoginPage(new NativeDriverImpl(new FakeScreen()));
            assertThatThrownBy(phone::open)
                    .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("open(url)");
        }

        @Test
        void cookieLoginAcceptsANativeDriverAndFailsAtRunTime() {
            Driver phone = new NativeDriverImpl(new FakeScreen());

            assertThatThrownBy(() -> com.study.principles.isp.bad.CookieLogin.loginAs(phone, "alice"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void testingAOneMethodClientNeedsAThirteenMethodFake() {
            FakeDriverForScreenshots fake = new FakeDriverForScreenshots();

            assertThatThrownBy(() -> new com.study.principles.isp.bad.ScreenshotOnFailure(fake)
                    .run("checkout", () -> { throw new AssertionError("wrong total"); }))
                    .isInstanceOf(AssertionError.class);

            assertThat(fake.taken).containsExactly("checkout");
            assertThat(Driver.class.getMethods()).as("methods the fake had to implement").hasSize(13);
        }
    }

    @Nested
    class Good {

        @Test
        void implementationsImplementOnlyTheirRolesNothingIsStubbed() {
            assertThat(unsupportedMethods(new PlaywrightBrowser(new FakeScreen()), Browser.class)).isEmpty();
            assertThat(unsupportedMethods(new AppiumDevice(new FakeScreen()), MobileApp.class)).isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.study.principles.isp.IspTest#platforms")
        void oneLoginPageDrivesWebAndNative(Function<FakeScreen, Interaction> platform) {
            LoginPage page = new LoginPage(platform.apply(new FakeScreen()));

            page.loginAs("alice", "alice-pass");

            assertThat(page.greeting()).isEqualTo("Hello, alice");
        }

        @Test
        void aOneMethodRoleIsFakedWithALambda() {
            List<String> taken = new ArrayList<>();
            ScreenshotOnFailure onFailure = new ScreenshotOnFailure(name -> {
                taken.add(name);
                return name;
            });

            assertThatThrownBy(() -> onFailure.run("checkout", () -> { throw new AssertionError("wrong total"); }))
                    .isInstanceOf(AssertionError.class);
            assertThat(taken).containsExactly("checkout");
        }

        @Test
        void cookieLoginTakesAnIntersectionOfRoles() {
            FakeScreen screen = new FakeScreen();
            PlaywrightBrowser browser = new PlaywrightBrowser(screen);
            // CookieLogin.loginAs(new AppiumDevice(screen), "alice")  — does not compile

            CookieLogin.loginAs(browser, "bob");

            assertThat(browser.currentUrl()).isEqualTo("/home");
            assertThat(browser.text("greeting")).isEqualTo("Hello, bob");
        }

        @Test
        void swipeCarouselTakesTwoRolesOfOneDevice() {
            AppiumDevice device = new AppiumDevice(new FakeScreen());

            SwipeCarousel carousel = new SwipeCarousel(device, device);

            assertThat(carousel.next()).isEqualTo("Track orders");
            assertThat(carousel.next()).isEqualTo("Get started");
        }
    }

    @Nested
    class Overapplied {

        @Test
        void interfacePerMethodWorksButTheRoleLostItsName() {
            PlaywrightBrowser browser = new PlaywrightBrowser(new FakeScreen());
            var page = new com.study.principles.isp.overapplied.LoginPage(browser::click, browser::type, browser::text);

            page.loginAs("alice", "alice-pass");

            assertThat(page.greeting()).isEqualTo("Hello, alice");
        }
    }

    static Stream<Named<Function<FakeScreen, Interaction>>> platforms() {
        return Stream.of(
                named("web: PlaywrightBrowser", PlaywrightBrowser::new),
                named("native: AppiumDevice", AppiumDevice::new));
    }

    /** Calls every method of {@code type} on {@code impl} with dummy arguments; names those that refuse. */
    private static List<String> unsupportedMethods(Object impl, Class<?> type) {
        List<String> refused = new ArrayList<>();
        for (Method method : type.getMethods()) {
            Object[] args = Arrays.stream(method.getParameterTypes()).map(p -> "x").toArray();
            try {
                method.invoke(impl, args);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof UnsupportedOperationException) refused.add(method.getName());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return refused;
    }
}
