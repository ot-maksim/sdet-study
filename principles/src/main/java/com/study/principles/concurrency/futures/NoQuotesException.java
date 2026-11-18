package com.study.principles.concurrency.futures;

import java.util.List;

/** No provider gave a price; the message says why for each of them. */
public final class NoQuotesException extends RuntimeException {

    public NoQuotesException(List<String> reasons) {
        super("No quotes: " + reasons);
    }
}
