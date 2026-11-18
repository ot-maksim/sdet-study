package com.study.principles.inheritance.pages.good;

/** No {@code sidebar()} here: what the page lacks does not compile, instead of failing at run time. */
public final class SettingsPage {

    private final Header header;
    private final SearchBox search;

    SettingsPage(Header header, SearchBox search) {
        this.header = header;
        this.search = search;
    }

    public Header header() {
        return header;
    }

    public SearchBox search() {
        return search;
    }
}
