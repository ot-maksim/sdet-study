package com.study.review.bad.core;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.study.review.bad.config.FrameworkConfig;
import java.util.Map;

/** Holds the browser for the tests. The browser is launched once, a fresh context is opened per test class. */
public final class Driver {

    private static Playwright playwright;
    private static Browser browser;
    private static BrowserContext context;
    private static Page page;

    private Driver() {
    }

    public static void start() {
        FrameworkConfig config = FrameworkConfig.getInstance();
        if (playwright == null) {
            playwright = Playwright.create(new Playwright.CreateOptions().setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(config.getBoolean("headless")));
        }
        context = browser.newContext(contextOptions(config.get("profile", "desktop")));
        page = context.newPage();
        page.setDefaultTimeout(config.getInt("ui.timeout", 30_000));
    }

    static Browser.NewContextOptions contextOptions(String profile) {
        return switch (profile) {
            case "desktop" -> new Browser.NewContextOptions().setViewportSize(1280, 800);
            case "mobile" -> new Browser.NewContextOptions().setViewportSize(390, 844).setIsMobile(true).setHasTouch(true);
            default -> throw new IllegalArgumentException("Unknown profile '" + profile + "', expected desktop or mobile");
        };
    }

    public static Page getPage() {
        return page;
    }

    public static boolean isStarted() {
        return page != null;
    }

    public static void stop() {
        if (context != null) {
            context.close();
        }
        context = null;
        page = null;
    }
}
