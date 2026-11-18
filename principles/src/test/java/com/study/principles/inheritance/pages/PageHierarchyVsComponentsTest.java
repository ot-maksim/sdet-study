package com.study.principles.inheritance.pages;

import com.study.principles.inheritance.pages.bad.DashboardPage;
import com.study.principles.inheritance.pages.bad.MobileDashboardPage;
import com.study.principles.inheritance.pages.bad.MobileSettingsPage;
import com.study.principles.inheritance.pages.bad.ReportsPage;
import com.study.principles.inheritance.pages.bad.SettingsPage;
import com.study.principles.inheritance.pages.good.Header;
import com.study.principles.inheritance.pages.good.Pages;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageHierarchyVsComponentsTest {

    @Nested
    class Bad {

        @Test
        void settingsInheritsANavigateToItCannotHonour() {
            SettingsPage settings = new SettingsPage(new FakeBrowser(Device.DESKTOP, "alice"));
            settings.open();

            assertThat(settings.search("report")).isEqualTo("monthly report, weekly report");
            assertThatThrownBy(() -> settings.navigateTo("reports"))   // compiles, autocompletes, fails
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("#nav-reports is not visible on /settings");
        }

        @Test
        void mobileNeedsAnOverridePerLeafAndOneWasForgotten() {
            FakeBrowser phone = new FakeBrowser(Device.MOBILE, "alice");
            ReportsPage reports = new ReportsPage(phone);   // there is no MobileReportsPage
            reports.open();

            assertThatThrownBy(reports::logout)
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("#logout is not visible on /reports (MOBILE)");
        }

        @Test
        void theBurgerMenuIsWrittenOncePerMobileLeaf() {
            List<Class<?>> overridingLogout = Stream.<Class<?>>of(MobileDashboardPage.class, MobileSettingsPage.class)
                    .filter(page -> declares(page, "logout"))
                    .toList();

            assertThat(overridingLogout).as("same override, copied: the two parents differ").hasSize(2);
        }

        @Test
        void theDesktopClassChosenOnMobileFailsOnlyAtRunTime() {
            DashboardPage dashboard = new DashboardPage(new FakeBrowser(Device.MOBILE, "alice"));
            dashboard.open();

            assertThatThrownBy(dashboard::userName).isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested
    class Good {

        /** One test for every page × device: the header is the same component wherever it appears. */
        @ParameterizedTest
        @EnumSource(Device.class)
        void everyPageWithAHeaderLogsOutOnEveryDevice(Device device) {
            List<Function<Pages, Header>> pagesWithHeader = List.of(
                    pages -> pages.dashboard().header(),
                    pages -> pages.reports().header(),
                    pages -> pages.settings().header());

            for (Function<Pages, Header> page : pagesWithHeader) {
                FakeBrowser browser = new FakeBrowser(device, "alice");
                Header header = page.apply(new Pages(browser));

                assertThat(header.userName()).isEqualTo("alice");
                header.logout();
                assertThat(browser.currentPath()).isEqualTo("/login");
            }
        }

        @ParameterizedTest
        @EnumSource(Device.class)
        void searchIsOneComponentOnDashboardAndSettings(Device device) {
            Pages pages = new Pages(new FakeBrowser(device, "alice"));

            assertThat(pages.dashboard().search().search("report")).isEqualTo("monthly report, weekly report");
            assertThat(pages.settings().search().search("settings")).isEqualTo("user settings");
        }

        @Test
        void sidebarNavigatesBetweenPagesThatHaveIt() {
            FakeBrowser browser = new FakeBrowser(Device.DESKTOP, "alice");

            new Pages(browser).dashboard().sidebar().navigateTo("reports");

            assertThat(browser.currentPath()).isEqualTo("/reports");
        }

        @Test
        void settingsHasNoSidebarSoTheMistakeDoesNotCompile() {
            assertThat(declares(com.study.principles.inheritance.pages.good.SettingsPage.class, "sidebar")).isFalse();
            assertThat(declares(com.study.principles.inheritance.pages.good.SettingsPage.class, "search")).isTrue();
        }
    }

    private static boolean declares(Class<?> type, String method) {
        return Arrays.stream(type.getDeclaredMethods()).anyMatch(m -> m.getName().equals(method));
    }
}
