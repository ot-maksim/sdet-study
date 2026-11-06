package com.study.principles.inheritance.session.bad;

import com.study.principles.inheritance.session.BrowserFarm;
import com.study.principles.inheritance.session.FakeBrowser;
import com.study.principles.inheritance.session.FakePage;

/**
 * A different author, different mistakes:
 *  1. close() is idempotent here (good!) — but nobody told WebSession
 *  2. page() checks nothing → the test keeps "working" with a dead session and fails later, far from the cause
 */
public class MobileSession extends DriverSession {
    private final BrowserFarm farm;
    private FakeBrowser device;
    private FakePage page;

    public MobileSession(BrowserFarm farm) {
        this.farm = farm;
    }

    @Override
    public void start() {
        device = farm.launch("android-emulator");
        page = device.newPage();
        started = true;
    }

    @Override
    public FakePage page() {
        return page;                       // no state check at all
    }

    @Override
    public void close() {
        if (closed) return;
        if (device != null) device.close();
        closed = true;
    }
}
