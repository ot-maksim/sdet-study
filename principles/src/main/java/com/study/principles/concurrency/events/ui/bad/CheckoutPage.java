package com.study.principles.concurrency.events.ui.bad;

import com.study.principles.concurrency.events.ui.Dialog;
import com.study.principles.concurrency.events.ui.FakePage;
import com.study.principles.concurrency.events.ui.Response;

import java.time.Duration;

import static com.study.principles.concurrency.events.ui.CheckoutApp.PAY;
import static com.study.principles.concurrency.events.ui.CheckoutApp.PAYMENTS_URL;
import static com.study.principles.concurrency.events.ui.CheckoutApp.STATUS;
import static com.study.principles.concurrency.events.ui.CheckoutApp.TOAST;

/**
 * A page object as it is often written: each method reads naturally top to bottom, and each one listens
 * <b>after</b> the thing it listens for may already have happened.
 */
public final class CheckoutPage {

    private final FakePage page;

    public CheckoutPage(FakePage page) {
        this.page = page;
    }

    /** "Click Pay, then confirm the dialog": the dialog came and was auto-dismissed during the click. */
    public void pay() {
        page.click(PAY);
        page.onceDialog(Dialog::accept);
    }

    /**
     * Confirms correctly, but arms the response waiter after the click and the step's screenshot: a response faster
     * than the screenshot is already gone, and the wait times out although the payment went through.
     */
    public Response submitPayment() {
        page.onceDialog(Dialog::accept);
        page.click(PAY);
        page.screenshot();   // the step reporter's "after each action" screenshot
        return page.waitForResponse(r -> r.url().equals(PAYMENTS_URL), () -> { });
    }

    /** A snapshot: whatever the status is at the moment of the call. */
    public String status() {
        return page.textContent(STATUS);
    }

    /** Hand-rolled polling every 500 ms for 2 s: a toast shorter than the interval can fall between two looks. */
    public boolean toastAppeared() {
        for (int i = 0; i < 4; i++) {
            if (page.isVisible(TOAST)) {
                return true;
            }
            sleep(Duration.ofMillis(500));
        }
        return page.isVisible(TOAST);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
