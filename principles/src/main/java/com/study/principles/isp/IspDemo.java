package com.study.principles.isp;

import com.study.principles.isp.bad.Driver;
import com.study.principles.isp.bad.NativeDriverImpl;
import com.study.principles.isp.bad.WebDriverImpl;
import com.study.principles.isp.good.AppLifecycle;
import com.study.principles.isp.good.AppiumDevice;
import com.study.principles.isp.good.Cookies;
import com.study.principles.isp.good.Gestures;
import com.study.principles.isp.good.Interaction;
import com.study.principles.isp.good.LoginPage;
import com.study.principles.isp.good.Navigation;
import com.study.principles.isp.good.PlaywrightBrowser;
import com.study.principles.isp.good.Screenshots;

import java.util.List;

/**
 * Interface sizes and who implements what; then the same login on web and native, bad vs good.
 *
 * mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.isp.IspDemo
 */
public class IspDemo {

    public static void main(String[] args) {
        System.out.println("=== interfaces: methods | PlaywrightBrowser | AppiumDevice ===");
        System.out.printf("  %-13s %2d   both implement it, 4 + 5 methods stubbed with UnsupportedOperationException%n",
                "bad.Driver", Driver.class.getMethods().length);
        for (Class<?> role : List.of(Navigation.class, Interaction.class, Screenshots.class, Cookies.class,
                Gestures.class, AppLifecycle.class)) {
            System.out.printf("  %-13s %2d   %-5s %-5s%n", role.getSimpleName(), role.getMethods().length,
                    role.isAssignableFrom(PlaywrightBrowser.class) ? "yes" : "-",
                    role.isAssignableFrom(AppiumDevice.class) ? "yes" : "-");
        }

        System.out.println();
        System.out.println("=== BAD: LoginPage(Driver) ===");
        login("web   ", () -> {
            var page = new com.study.principles.isp.bad.LoginPage(new WebDriverImpl(new FakeScreen())).open();
            page.loginAs("alice", "alice-pass");
            return page.greeting();
        });
        login("native", () -> {
            var page = new com.study.principles.isp.bad.LoginPage(new NativeDriverImpl(new FakeScreen())).open();
            page.loginAs("alice", "alice-pass");
            return page.greeting();
        });
        login("web.swipe(\"left\")", () -> {
            new WebDriverImpl(new FakeScreen()).swipe("left");   // compiles: it is on Driver
            return "?";
        });

        System.out.println("=== GOOD: LoginPage(Interaction) ===");
        login("web   ", () -> {
            PlaywrightBrowser browser = new PlaywrightBrowser(new FakeScreen());
            browser.open("/login");
            LoginPage page = new LoginPage(browser);
            page.loginAs("alice", "alice-pass");
            return page.greeting();
        });
        login("native", () -> {
            AppiumDevice device = new AppiumDevice(new FakeScreen());
            device.installApp("shop.apk");
            device.launchApp("com.shop");
            LoginPage page = new LoginPage(device);
            page.loginAs("alice", "alice-pass");
            return page.greeting();
        });
        System.out.println("  browser.swipe(\"left\") -> does not compile");
    }

    private static void login(String name, java.util.function.Supplier<String> scenario) {
        try {
            System.out.println("  " + name + " -> " + scenario.get());
        } catch (RuntimeException e) {
            System.out.println("  " + name + " -> FAILS: " + e.getMessage());
        }
    }
}
