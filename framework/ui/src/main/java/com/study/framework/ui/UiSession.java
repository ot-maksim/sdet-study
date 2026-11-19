package com.study.framework.ui;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import java.nio.file.Path;

/**
 * One test's UI: a leased browser, a fresh context (own cookies and storage — no state leaks between tests)
 * with the profile and the base URL, and one page. Test-scoped; closing it closes the context and returns the
 * browser to the pool. Pages navigate with relative paths: {@code page.navigate("/users")}.
 */
public final class UiSession implements AutoCloseable {

    private final BrowserPool.Lease lease;
    private final BrowserContext context;
    private final Page page;

    public UiSession(BrowserPool pool, BrowserProfile profile, UiSettings settings) {
        this.lease = pool.lease();
        try {
            this.context = lease.browser().newContext(profile.contextOptions().get().setBaseURL(settings.baseUrl().toString()));
            context.setDefaultTimeout(settings.timeout().toMillis());
            this.page = context.newPage();
        } catch (RuntimeException e) {
            lease.close();
            throw e;
        }
    }

    public Page page() {
        return page;
    }

    /** Own timeout: called after a failure, often a timeout, so the context's default would fail it too. */
    public void screenshot(Path file) {
        page.screenshot(new Page.ScreenshotOptions().setPath(file).setFullPage(true).setTimeout(10_000));
    }

    @Override
    public void close() {
        try {
            context.close();
        } finally {
            lease.close();
        }
    }
}
