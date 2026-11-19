package com.study.review.good;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.study.review.Stand;
import com.study.review.good.api.UsersClient;
import com.study.review.good.config.Settings;
import com.study.review.good.data.Cleanup;
import com.study.review.good.data.TestUsers;
import com.study.review.good.db.UserRows;
import com.study.review.good.steps.UserSteps;
import com.study.review.good.ui.UsersPage;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * The composition root of the refactored framework: the one place with {@code new}, the stand and the lifetimes.
 * A test gets what it declares as parameters and nothing else — an API test never launches a browser or opens a
 * database connection.
 *
 * <ul>
 *   <li>per run (root store, closed at the end): settings, the API client, test data, the browser</li>
 *   <li>per test (method store, closed after the test, passed or failed): cleanup, the browser context</li>
 * </ul>
 *
 * Tests here run on one thread; for parallel UI tests the browser is leased from a pool — {@code framework/ui}.
 * The full root with modules, config layers and stand policy: {@code framework/tests} (ADR 0001, 0007).
 */
public final class GoodRoot implements ParameterResolver {

    /** Observation for the review tests only: how many browsers this root launched. */
    public static final AtomicInteger BROWSER_LAUNCHES = new AtomicInteger();

    private static final Namespace NS = Namespace.create(GoodRoot.class);
    private static final Set<Class<?>> PROVIDED = Set.of(
            Settings.class, UsersClient.class, TestUsers.class, UserSteps.class, UsersPage.class, UserRows.class);

    private record Browsers(Playwright playwright, Browser browser) implements AutoCloseable {
        static Browsers launch() {
            // Chromium is installed explicitly (framework/ui/README.md); do not download every browser on start
            Playwright playwright = Playwright.create(new Playwright.CreateOptions()
                    .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
            BROWSER_LAUNCHES.incrementAndGet();
            return new Browsers(playwright, playwright.chromium().launch());
        }

        @Override
        public void close() {
            playwright.close();
        }
    }

    private record Tab(BrowserContext context, Page page) implements AutoCloseable {
        @Override
        public void close() {
            context.close();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return PROVIDED.contains(parameter.getParameter().getType());
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        Class<?> type = parameter.getParameter().getType();
        if (type == Settings.class) {
            return settings(context);
        }
        if (type == UsersClient.class) {
            return client(context);
        }
        if (type == TestUsers.class) {
            return testUsers(context);
        }
        if (type == UserSteps.class) {
            return new UserSteps(client(context), testUsers(context), perTest(context, Cleanup.class, Cleanup::new));
        }
        if (type == UsersPage.class) {
            return new UsersPage(perTest(context, Tab.class, () -> openTab(context)).page());
        }
        if (type == UserRows.class) {
            return new UserRows(settings(context).db().orElseThrow());
        }
        throw new IllegalStateException("not provided: " + type);
    }

    private static Settings settings(ExtensionContext context) {
        // The only place that knows the stand: the in-process SUT, and the compose Postgres for table checks
        return perRun(context, Settings.class, () -> Settings.from(Map.of(
                "base.url", Stand.shared().url(),
                "timeout.ms", "5000",
                "db.url", "jdbc:postgresql://localhost:5432/sut",
                "db.user", "sut",
                "db.password", "sut")));
    }

    private static UsersClient client(ExtensionContext context) {
        return perRun(context, UsersClient.class, () -> {
            Settings settings = settings(context);
            return new UsersClient(settings.baseUrl(), settings.timeout());
        });
    }

    private static TestUsers testUsers(ExtensionContext context) {
        return perRun(context, TestUsers.class, TestUsers::forNewRun);
    }

    private static Tab openTab(ExtensionContext context) {
        Settings settings = settings(context);
        Browser browser = perRun(context, Browsers.class, Browsers::launch).browser();
        BrowserContext tab = browser.newContext(new Browser.NewContextOptions()
                .setBaseURL(settings.baseUrl().toString())
                .setViewportSize(1280, 800));
        tab.setDefaultTimeout(settings.timeout().toMillis());
        return new Tab(tab, tab.newPage());
    }

    private static <T> T perRun(ExtensionContext context, Class<T> type, java.util.function.Supplier<T> create) {
        return context.getRoot().getStore(NS).getOrComputeIfAbsent(type, k -> create.get(), type);
    }

    private static <T> T perTest(ExtensionContext context, Class<T> type, java.util.function.Supplier<T> create) {
        return context.getStore(NS).getOrComputeIfAbsent(type, k -> create.get(), type);
    }
}
