package com.study.principles.inheritance.pages.good;

/** A page is the list of components it really has — HAS-A, not a position in a tree. */
public final class DashboardPage {

    private final Header header;
    private final Sidebar sidebar;
    private final SearchBox search;

    DashboardPage(Header header, Sidebar sidebar, SearchBox search) {
        this.header = header;
        this.sidebar = sidebar;
        this.search = search;
    }

    public Header header() {
        return header;
    }

    public Sidebar sidebar() {
        return sidebar;
    }

    public SearchBox search() {
        return search;
    }
}
