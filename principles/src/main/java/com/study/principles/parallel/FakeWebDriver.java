package com.study.principles.parallel;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Fake browser session with the threading contract of a real {@code WebDriver} (or a Playwright
 * {@code Page}): one current page, one cookie jar, no synchronization. Whoever drives it last owns it,
 * so two tests sharing one instance see each other's cookies and pages.
 *
 * <p>{@link AutoCloseable} so that a JUnit {@code Store} quits it automatically after the test.
 */
public final class FakeWebDriver implements AutoCloseable {

    private final FakeShop shop;
    private final Map<String, String> cookies = new HashMap<>(); // not thread-safe on purpose: neither is a browser
    private FakeShop.Page page = FakeShop.Page.BLANK;
    private boolean quit;

    public FakeWebDriver(FakeShop shop) {
        this.shop = shop;
    }

    public void get(String path) {
        requireAlive();
        page = shop.render(path, cookies.get("session"));
    }

    /** Selenium: {@code driver.manage().addCookie(new Cookie(name, value))}. */
    public void addCookie(String name, String value) {
        requireAlive();
        cookies.put(name, value);
    }

    public String getCurrentUrl() {
        requireAlive();
        return page.url();
    }

    /** Selenium: {@code driver.findElement(By.id(id)).getText()}. */
    public String findText(String id) {
        requireAlive();
        String text = page.elements().get(id);
        if (text == null) {
            throw new NoSuchElementException("no element #" + id + " on " + page.url());
        }
        return text;
    }

    public void quit() {
        quit = true;
    }

    public boolean isAlive() {
        return !quit;
    }

    @Override
    public void close() {
        quit();
    }

    private void requireAlive() {
        if (quit) {
            throw new IllegalStateException("session is quit");
        }
    }
}
