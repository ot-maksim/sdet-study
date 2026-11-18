package com.study.principles.concurrency.events.ui.good;

import com.study.principles.concurrency.events.ui.Dialog;
import com.study.principles.concurrency.events.ui.Expect;
import com.study.principles.concurrency.events.ui.FakePage;
import com.study.principles.concurrency.events.ui.Response;

import java.util.concurrent.atomic.AtomicReference;

import static com.study.principles.concurrency.events.ui.CheckoutApp.PAY;
import static com.study.principles.concurrency.events.ui.CheckoutApp.PAYMENTS_URL;
import static com.study.principles.concurrency.events.ui.CheckoutApp.STATUS;
import static com.study.principles.concurrency.events.ui.CheckoutApp.TOAST;

/**
 * The same page object, with every listener armed <b>before</b> the action:
 * <ul>
 *   <li>the dialog handler is registered before the click ({@code onceDialog}: it does not leak into later steps);</li>
 *   <li>the click runs inside {@code waitForResponse}: the waiter exists before the request can be answered;</li>
 *   <li>state is exposed as retrying assertions, not as snapshots;</li>
 *   <li>the dialog's message is recorded in the handler and asserted by the test, in the test thread.</li>
 * </ul>
 */
public final class CheckoutPage {

    private final FakePage page;
    private final AtomicReference<String> lastDialog = new AtomicReference<>();

    public CheckoutPage(FakePage page) {
        this.page = page;
    }

    /** Confirms the dialog and returns the payment response. */
    public Response pay() {
        page.onceDialog(dialog -> {
            lastDialog.set(dialog.message());   // record, do not assert: this runs on the browser thread
            dialog.accept();
        });
        return page.waitForResponse(r -> r.url().equals(PAYMENTS_URL), () -> {
            page.click(PAY);
            page.screenshot();   // the step reporter may take its time: the waiter is already armed
        });
    }

    /** Dismisses the dialog: nothing is sent. */
    public void cancel() {
        page.onceDialog(Dialog::dismiss);
        page.click(PAY);
    }

    public String lastDialogMessage() {
        return lastDialog.get();
    }

    public Expect.LocatorAssert status() {
        return Expect.that(page, STATUS);
    }

    public Expect.LocatorAssert toast() {
        return Expect.that(page, TOAST);
    }
}
