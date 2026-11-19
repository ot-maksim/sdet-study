package com.study.framework.core.lifecycle;

/** Some cleanup actions of a scope failed; each failure is a suppressed exception, the message lists them. */
public class CleanupFailedException extends RuntimeException {

    public CleanupFailedException(String message) {
        super(message);
    }
}
