package com.study.principles.inheritance.session.good;

import com.study.principles.inheritance.session.BrowserFarm;
import com.study.principles.inheritance.session.FakeBrowser;
import com.study.principles.inheritance.session.FakePage;

/** A second author gets the same guarantees for free — compare with bad.MobileSession. */
public class MobileSession extends DriverSession {
    private final BrowserFarm farm;
    private FakeBrowser device;
    private FakePage page;

    public MobileSession(BrowserFarm farm) {
        this.farm = farm;
    }

    @Override
    protected void doStart() {
        device = farm.launch("android-emulator");
        page = device.newPage();
    }

    @Override
    protected void doRelease() {
        if (device != null) device.close();
    }

    public FakePage page() {
        ensureStarted();
        return page;
    }
}
