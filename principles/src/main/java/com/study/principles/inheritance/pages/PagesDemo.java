package com.study.principles.inheritance.pages;

import com.study.principles.inheritance.pages.bad.DashboardPage;
import com.study.principles.inheritance.pages.bad.MobileDashboardPage;
import com.study.principles.inheritance.pages.bad.MobileSettingsPage;
import com.study.principles.inheritance.pages.bad.ReportsPage;
import com.study.principles.inheritance.pages.bad.SettingsPage;
import com.study.principles.inheritance.pages.good.Pages;

import java.util.function.Supplier;

/**
 * The same three scenarios on desktop and mobile: log out from every page, search on settings,
 * navigate from settings. Bad picks a class per page × device (and one is missing); good picks nothing.
 *
 * mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.inheritance.pages.PagesDemo
 */
public class PagesDemo {

    public static void main(String[] args) {
        for (Device device : Device.values()) {
            System.out.println("=== BAD hierarchy, " + device + " ===");
            boolean mobile = device == Device.MOBILE;
            step("dashboard.logout()", () -> {
                FakeBrowser b = new FakeBrowser(device, "alice");
                DashboardPage page = mobile ? new MobileDashboardPage(b) : new DashboardPage(b);
                page.open();
                page.logout();
                return b.currentPath();
            });
            step("reports.logout()  ", () -> {
                FakeBrowser b = new FakeBrowser(device, "alice");
                ReportsPage page = new ReportsPage(b);   // no MobileReportsPage
                page.open();
                page.logout();
                return b.currentPath();
            });
            step("settings.logout() ", () -> {
                FakeBrowser b = new FakeBrowser(device, "alice");
                SettingsPage page = mobile ? new MobileSettingsPage(b) : new SettingsPage(b);
                page.open();
                page.logout();
                return b.currentPath();
            });
            step("settings.navigateTo(\"reports\")", () -> {
                SettingsPage page = new SettingsPage(new FakeBrowser(device, "alice"));
                page.open();
                page.navigateTo("reports");   // inherited, compiles
                return "?";
            });

            System.out.println("=== GOOD components, " + device + " ===");
            step("dashboard.header().logout()", () -> {
                FakeBrowser b = new FakeBrowser(device, "alice");
                new Pages(b).dashboard().header().logout();
                return b.currentPath();
            });
            step("reports.header().logout()  ", () -> {
                FakeBrowser b = new FakeBrowser(device, "alice");
                new Pages(b).reports().header().logout();
                return b.currentPath();
            });
            step("settings.header().logout() ", () -> {
                FakeBrowser b = new FakeBrowser(device, "alice");
                new Pages(b).settings().header().logout();
                return b.currentPath();
            });
            System.out.println("  settings.sidebar()             -> does not compile");
            System.out.println();
        }
    }

    private static void step(String name, Supplier<String> action) {
        try {
            System.out.println("  " + name + " -> " + action.get());
        } catch (RuntimeException e) {
            System.out.println("  " + name + " -> FAILS: " + e.getMessage());
        }
    }
}
