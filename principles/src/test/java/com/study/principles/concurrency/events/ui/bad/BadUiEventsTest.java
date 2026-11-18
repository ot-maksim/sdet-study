package com.study.principles.concurrency.events.ui.bad;

import com.study.principles.concurrency.events.ui.CheckoutApp;
import com.study.principles.concurrency.events.ui.Dialog;
import com.study.principles.concurrency.events.ui.Expect;
import com.study.principles.concurrency.events.ui.FakePage;
import com.study.principles.concurrency.events.ui.TimeoutError;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.study.principles.concurrency.events.ui.CheckoutApp.PAY;
import static com.study.principles.concurrency.events.ui.CheckoutApp.STATUS;
import static com.study.principles.concurrency.events.ui.CheckoutApp.TOAST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Characterization tests: they pass <i>because</i> they pin the bugs of {@link CheckoutPage}. */
class BadUiEventsTest {

    private FakePage page;

    @AfterEach
    void close() {
        page.close();
    }

    private CheckoutPage open(Duration apiLatency, Duration toastDuration) {
        page = CheckoutApp.open(apiLatency, toastDuration);
        return new CheckoutPage(page);
    }

    // --- 1. Dialog handler after the click ------------------------------------------------------------------

    @Test
    void dialogHandlerAfterTheClick_dialogWasAutoDismissed_paymentCancelled() {
        CheckoutPage checkout = open(Duration.ofMillis(50), Duration.ofSeconds(2));

        checkout.pay();

        Expect.that(page, STATUS).hasText("CANCELLED");   // the test meant to pay
    }

    // --- 2. Response waiter armed after the action -------------------------------------------------------------

    @Test
    void responseWaiterArmedAfterTheScreenshot_missesAFastResponse_thoughThePaymentWentThrough() {
        CheckoutPage checkout = open(Duration.ZERO, Duration.ofSeconds(2));   // a cached or stubbed API
        page.setDefaultTimeout(Duration.ofMillis(500));

        assertThatThrownBy(checkout::submitPayment).isInstanceOf(TimeoutError.class);
        Expect.that(page, STATUS).hasText("PAID");
    }

    // --- 3. Snapshot instead of a retrying assertion -------------------------------------------------------------

    @Test
    void snapshotRightAfterTheClick_seesTheIntermediateState() {
        CheckoutPage checkout = open(Duration.ofMillis(200), Duration.ofSeconds(2));
        page.onceDialog(Dialog::accept);

        page.click(PAY);

        assertThat(checkout.status()).isEqualTo("PROCESSING");   // assertThat(status()).isEqualTo("PAID") fails
    }

    // --- 4. A transient element ----------------------------------------------------------------------------------

    @Test
    void pollingEvery500ms_missesAToastShorterThanTheInterval() {
        CheckoutPage checkout = open(Duration.ofMillis(100), Duration.ofMillis(200));   // visible from ~100 to ~300 ms
        page.onceDialog(Dialog::accept);

        page.click(PAY);

        assertThat(checkout.toastAppeared()).isFalse();   // looked at ~0 ms and ~500 ms
    }

    @Test
    void slowChecksBeforeTheToast_evenARetryingAssertionComesTooLate() {
        open(Duration.ZERO, Duration.ofMillis(300));
        page.onceDialog(Dialog::accept);
        page.click(PAY);
        Expect.that(page, STATUS).hasText("PAID");

        for (int i = 0; i < 6; i++) {
            page.screenshot();   // other checks first: the order in the DB, the email, the step screenshots
        }

        assertThatThrownBy(() -> Expect.that(page, TOAST).withTimeout(Duration.ofSeconds(1)).isVisible())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("but was not on the page");
    }
}
