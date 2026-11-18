package com.study.principles.kiss;

import com.study.principles.kiss.flags.FakeShop;
import com.study.principles.kiss.flags.FlagAudit;
import com.study.principles.kiss.flags.FlagAudit.Flags;
import com.study.principles.kiss.flags.FlagAudit.Row;
import com.study.principles.kiss.flags.FlagAudit.Verdict;
import com.study.principles.kiss.flags.Order;
import com.study.principles.kiss.flags.good.Checkout;
import com.study.principles.kiss.flags.good.CreatedOrders;
import com.study.principles.kiss.flags.good.CustomerCart;
import com.study.principles.kiss.flags.good.GuestCart;
import com.study.principles.kiss.flags.good.PaidOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlagStepTest {

    @Nested
    class Bad {

        @Test
        void fiveFlagsAreThirtyTwoCasesAndMostOfThemAreWrong() {
            Map<Verdict, Long> verdicts = FlagAudit.auditAll().stream()
                    .collect(Collectors.groupingBy(Row::verdict, Collectors.counting()));

            assertThat(verdicts).containsOnly(
                    Map.entry(Verdict.OK, 13L),
                    Map.entry(Verdict.BROKEN, 4L),
                    Map.entry(Verdict.LIES, 7L),
                    Map.entry(Verdict.DEAD_FLAG, 8L));
        }

        @Test
        void aGuestWithASavedCardIsAcceptedBySignatureAndFailsAtRunTime() {
            Row row = FlagAudit.audit(new Flags(true, false, true, true, false));

            assertThat(row.verdict()).isEqualTo(Verdict.BROKEN);
            assertThat(row.why()).contains("has no saved card");
        }

        @Test
        void verifyWithAPromoPassesWhenThePromoIsBroken() {
            Row row = FlagAudit.audit(new Flags(false, true, true, false, true));

            assertThat(row.verdict()).isEqualTo(Verdict.LIES);
            assertThat(row.why()).contains("promo is broken");
        }

        @Test
        void verifyWithoutPayChecksNothing() {
            Row row = FlagAudit.audit(new Flags(false, false, false, false, true));

            assertThat(row.verdict()).isEqualTo(Verdict.DEAD_FLAG);
            assertThat(row.why()).isEqualTo("verify changes nothing");
        }

        @Test
        void aSavedCardWithoutPayIsSilentlyIgnored() {
            Row row = FlagAudit.audit(new Flags(false, false, false, true, false));

            assertThat(row.verdict()).isEqualTo(Verdict.DEAD_FLAG);
            assertThat(row.why()).isEqualTo("savedCard changes nothing");
        }
    }

    @Nested
    class Good {

        private final FakeShop shop = new FakeShop();
        private final CreatedOrders created = new CreatedOrders(shop);
        private final Checkout checkout = new Checkout(shop, created);
        private final String alice = shop.registerCustomer("alice");

        @AfterEach
        void cleanUp() {
            created.deleteAll();
        }

        @Test
        void aGuestPaysWithACard() {
            Order order = checkout.asGuest().payWith("4111111111111111").order();

            assertThat(order.customer()).isNull();
            assertThat(order.status()).isEqualTo(Order.Status.PAID);
            assertThat(order.paidWith()).isEqualTo("card *1111");
        }

        @Test
        void aCustomerPaysWithTheSavedCard() {
            Order order = checkout.as(alice).payWithSavedCard().order();

            assertThat(order.paidWith()).isEqualTo("saved:alice");
        }

        @Test
        void thePromoTakesFiveOff() {
            Order order = checkout.as(alice).withPromo("SAVE5").payWithSavedCard().order();

            assertThat(order.total()).isEqualTo(95);
        }

        @Test
        void anUnpaidOrderStaysPending() {
            Order order = checkout.asGuest().leaveUnpaid().order();

            assertThat(order.status()).isEqualTo(Order.Status.PENDING);
        }

        @Test
        void eachRegressionIsCaughtByTheTestThatCaresAboutIt() {
            Function<FakeShop.Build, Checkout> on = build -> {
                FakeShop broken = new FakeShop(build);
                broken.registerCustomer("alice");
                return new Checkout(broken, new CreatedOrders(broken));
            };

            PaidOrder discounted = on.apply(FakeShop.Build.PROMO_BROKEN).as("alice").withPromo("SAVE5").payWithSavedCard();
            assertThatThrownBy(() -> assertThat(discounted.order().total()).isEqualTo(95))
                    .isInstanceOf(AssertionError.class);

            PaidOrder paid = on.apply(FakeShop.Build.PAYMENT_BROKEN).asGuest().payWith("4111111111111111");
            assertThatThrownBy(() -> assertThat(paid.order().status()).isEqualTo(Order.Status.PAID))
                    .isInstanceOf(AssertionError.class);
        }

        @Test
        void aGuestCartHasNoSavedCardToPayWith() {
            // checkout.asGuest().payWithSavedCard() does not compile; reflection only makes it visible here
            assertThat(methodNames(GuestCart.class)).doesNotContain("payWithSavedCard");
            assertThat(methodNames(CustomerCart.class)).contains("payWithSavedCard");
        }

        @Test
        void cleanupIsTheLifecyclesJobNotAFlag() {
            checkout.asGuest().leaveUnpaid();
            checkout.as(alice).payWithSavedCard();
            assertThat(shop.orderCount()).isEqualTo(2);

            created.deleteAll();

            assertThat(shop.orderCount()).isZero();
        }

        private static java.util.List<String> methodNames(Class<?> type) {
            return Arrays.stream(type.getDeclaredMethods()).map(Method::getName).toList();
        }
    }
}
