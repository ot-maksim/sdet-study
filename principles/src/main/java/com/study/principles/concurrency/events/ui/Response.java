package com.study.principles.concurrency.events.ui;

/** A network response the page received, as Playwright's {@code Response}: what the test can wait for. */
public record Response(String method, String url, int status, String body) {
}
