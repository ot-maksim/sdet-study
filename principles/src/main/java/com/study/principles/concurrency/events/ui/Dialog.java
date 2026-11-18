package com.study.principles.concurrency.events.ui;

/**
 * A JavaScript {@code alert} / {@code confirm} / {@code prompt}, as Playwright's {@code Dialog}. The page is
 * blocked until a handler accepts or dismisses it. Handlers run on the browser thread.
 */
public final class Dialog {

    private final String message;
    private Boolean accepted;   // browser thread only

    Dialog(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }

    public void accept() {
        decide(true);
    }

    public void dismiss() {
        decide(false);
    }

    boolean handled() {
        return accepted != null;
    }

    boolean accepted() {
        return Boolean.TRUE.equals(accepted);
    }

    private void decide(boolean accept) {
        if (accepted != null) {
            throw new IllegalStateException("Dialog \"" + message + "\" is already handled");
        }
        accepted = accept;
    }
}
