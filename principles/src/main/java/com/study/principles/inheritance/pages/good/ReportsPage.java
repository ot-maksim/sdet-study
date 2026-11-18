package com.study.principles.inheritance.pages.good;

public final class ReportsPage {

    private final Header header;
    private final Sidebar sidebar;

    ReportsPage(Header header, Sidebar sidebar) {
        this.header = header;
        this.sidebar = sidebar;
    }

    public Header header() {
        return header;
    }

    public Sidebar sidebar() {
        return sidebar;
    }
}
