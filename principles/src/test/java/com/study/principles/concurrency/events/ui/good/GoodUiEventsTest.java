package com.study.principles.concurrency.events.ui.good;

import com.study.principles.concurrency.events.ui.CheckoutApp;
import com.study.principles.concurrency.events.ui.Dialog;
import com.study.principles.concurrency.events.ui.FakePage;
import com.study.principles.concurrency.events.ui.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.study.principles.concurrency.events.ui.CheckoutApp.PAY;
import static com.study.principles.concurrency.events.ui.CheckoutApp.TOAST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The scenarios of {@code bad.BadUiEventsTest}, done right. */
class GoodUiEventsTest {

    private FakePage page;

    @AfterEach
    void close() {
        page.close();
    }

    private CheckoutPage open(Duration apiLatency, Duration toastDuration) {
        page = CheckoutApp.open(apiLatency, toastDuration);
        return new CheckoutPage(page);
    }

    // --- 1. Dialog handler before the click -------------------------------------------------------------------

    @Test
    void dialogHandlerBeforeTheClick_paymentGoesThrough_messageAssertedInTheTest() {
        CheckoutPage checkout = open(Duration.ofMillis(50), Duration.ofSeconds(2));

        Response response = checkout.pay();

        assertThat(response.status()).isEqualTo(201);
        checkout.status().hasText("PAID");
        assertThat(checkout.lastDialogMessage()).isEqualTo(CheckoutApp.DIALOG);
    }

    @Test
    void dismissingOnPurpose_cancels() {
        CheckoutPage checkout = open(Duration.ofMillis(50), Duration.ofSeconds(2));

        checkout.cancel();

        checkout.status().hasText("CANCELLED");
    }

    // --- 2. Run and wait --------------------------------------------------------------------------------------

    @Test
    void runAndWait_catchesEvenAnInstantResponse() {
        CheckoutPage checkout = open(Duration.ZERO, Duration.ofSeconds(2));
        page.setDefaultTimeout(Duration.ofMillis(500));

        Response response = checkout.pay();   // click and a 100 ms screenshot inside: the waiter was armed first

        assertThat(response.body()).contains("\"paymentId\":\"pay_1\"");
    }

    // --- 3. Retrying assertions ---------------------------------------------------------------------------------

    @Test
    void retryingAssertion_waitsThroughTheIntermediateState() {
        CheckoutPage checkout = open(Duration.ofMillis(200), Duration.ofSeconds(2));
        page.onceDialog(Dialog::accept);

        page.click(PAY);

        checkout.status().hasText("PAID");
    }

    @Test
    void retryingAssertion_failsWithWhatItSawLast() {
        CheckoutPage checkout = open(Duration.ZERO, Duration.ofSeconds(2));
        checkout.pay();

        assertThatThrownBy(() -> checkout.status().withTimeout(Duration.ofMillis(300)).hasText("REFUNDED"))
                .isInstanceOf(AssertionError.class)
                .hasMessage("Locator #status expected to have text \"REFUNDED\" within PT0.3S, but was \"PAID\"");
    }

    // --- 4. A transient element ---------------------------------------------------------------------------------

    @Test
    void retryingAssertionRightAfterTheAction_catchesAShortToast() {
        CheckoutPage checkout = open(Duration.ofMillis(100), Duration.ofMillis(200));
        page.onceDialog(Dialog::accept);

        page.click(PAY);

        checkout.toast().isVisible();   // the first check after the action: woken by the DOM change
        checkout.toast().isHidden();
    }

    @Test
    void installedClock_theToastWaitsForTheTest_andItsTimeoutIsTestedExactly() {
        CheckoutPage checkout = open(Duration.ZERO, Duration.ofMillis(300));
        page.clock().install();   // before the action: the toast's setTimeout goes to the fake clock

        checkout.pay();
        checkout.status().hasText("PAID");
        for (int i = 0; i < 6; i++) {
            page.screenshot();    // the same slow checks that made the bad test miss the toast
        }

        checkout.toast().hasText("Payment successful");
        page.clock().runFor(Duration.ofMillis(299));
        assertThat(page.isVisible(TOAST)).as("still visible at 299 ms").isTrue();
        page.clock().runFor(Duration.ofMillis(1));
        assertThat(page.isVisible(TOAST)).as("hidden at 300 ms").isFalse();
    }
}
