package com.study.principles.inheritance.session.bad;

import com.study.principles.inheritance.session.BrowserFarm;
import com.study.principles.inheritance.session.FakeBrowser;
import com.study.principles.inheritance.session.FakePage;

/**
 * Mistakes made here:
 *  1. start() fails halfway → the browser is already launched; if the caller never got the session reference, it leaks
 *  2. close() never sets closed = true → second close() (e.g. test + @AfterEach) throws
 *  3. start() can be called twice → two browsers, the first one leaks
 */
public class WebSession extends DriverSession {
    private final BrowserFarm farm;
    private FakeBrowser browser;
    private FakePage page;

    public WebSession(BrowserFarm farm) {
        this.farm = farm;
    }

    @Override
    public void start() {
        browser = farm.launch("chromium");
        page = browser.newPage();          // if this throws, browser stays open
        started = true;
    }

    @Override
    public FakePage page() {
        if (!started) throw new IllegalStateException("not started");
        return page;                       // checks "started" but not "closed"
    }

    @Override
    public void close() {
        browser.close();                   // NPE if start() never ran, ISE on second call
    }
}
