package com.study.principles.inheritance.session;

/**
 * Like many real drivers (e.g. Appium quit() on a dead session), a second close() throws.
 */
public class FakeBrowser {
    private final String name;
    private final BrowserFarm farm;
    private boolean open = true;

    FakeBrowser(String name, BrowserFarm farm) {
        this.name = name;
        this.farm = farm;
    }

    public FakePage newPage() {
        if (!open) throw new IllegalStateException(name + ": browser is closed");
        if (farm.shouldFailOnNewPage()) throw new RuntimeException(name + ": failed to create page");
        return new FakePage(this);
    }

    public void close() {
        if (!open) throw new IllegalStateException(name + ": browser already closed");
        open = false;
    }

    public boolean isOpen() {
        return open;
    }

    @Override
    public String toString() {
        return name;
    }
}
