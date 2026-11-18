package com.study.principles.srp;

import com.study.principles.srp.good.CartPrecondition;
import com.study.principles.srp.good.Cards;
import com.study.principles.srp.good.CheckoutPage;
import com.study.principles.srp.good.ExpectedPrice;
import com.study.principles.srp.good.Orders;
import com.study.principles.srp.good.ReportingBrowser;
import com.study.principles.srp.overapplied.CheckoutFlow;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SrpTest {

    private static final List<Item> CART = List.of(Item.of("book", "40.00", 2), Item.of("pen", "5.50", 4));
    // subtotal 102.00 → discount 10% → 91.80 → VAT 20% → 110.16
    private static final BigDecimal CART_TOTAL = new BigDecimal("110.16");

    private final FakeShop shop = new FakeShop();
    private final ShopApi api = new ShopApi(shop);
    private final ShopDb db = new ShopDb(shop);

    @Nested
    class Bad {

        @Test
        void beforeTheRedesignTheAllInOnePageWorks() {
            var page = new com.study.principles.srp.bad.CheckoutPage(new FakeBrowser(shop, Markup.V1, "alice"), api, db);

            page.prepareCart("alice", CART).verifyTotal(CART);
            String orderId = page.payWith(page.randomCard());
            page.assertOrderInDb(orderId, CART);
        }

        @Test
        void afterTheRedesignAnApiAndDbTestBreaksToo() {
            // this test never looks at the UI: it only borrows the page's API and DB helpers
            FakeBrowser redesigned = new FakeBrowser(shop, Markup.V2, "alice");

            assertThatThrownBy(() -> {
                var page = new com.study.principles.srp.bad.CheckoutPage(redesigned, api, db);
                page.prepareCart("alice", CART);
                String orderId = api.placeOrder("alice", page.randomCard());
                page.assertOrderInDb(orderId, CART);
            }).isInstanceOf(IllegalStateException.class).hasMessageContaining("#checkout-form");
        }

        @Test
        void reportingIsWiredToStdoutAndPrintsTheCardInClear() {
            var page = new com.study.principles.srp.bad.CheckoutPage(new FakeBrowser(shop, Markup.V1, "alice"), api, db);
            page.prepareCart("alice", CART);

            String out = captureStdout(() -> page.payWith(Cards.validVisa()));

            assertThat(out).contains("[STEP] pay with card 4111111111111111");
        }
    }

    @Nested
    class Good {

        @Test
        void checkoutThroughTheUiWithTheOracleAndTheDbCheckSeparate() {
            new CartPrecondition(api).fill("alice", CART);
            CheckoutPage page = CheckoutPage.open(new FakeBrowser(shop, Markup.V1, "alice"));

            assertThat(page.total()).isEqualByComparingTo(ExpectedPrice.of(CART));
            String orderId = page.payWith(Cards.validVisa());

            assertThat(new Orders(db).get(orderId).total()).isEqualByComparingTo(ExpectedPrice.of(CART));
        }

        @Test
        void afterTheRedesignOnlyTheUiTestBreaks() {
            // API + DB test: no page involved, the redesign does not reach it
            new CartPrecondition(api).fill("alice", CART);
            String orderId = api.placeOrder("alice", Cards.validVisa());
            assertThat(new Orders(db).get(orderId).total()).isEqualByComparingTo(ExpectedPrice.of(CART));

            // UI test: fails, and the fix is the selectors of one class
            FakeBrowser redesigned = new FakeBrowser(shop, Markup.V2, "bob");
            assertThatThrownBy(() -> CheckoutPage.open(redesigned))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("#checkout-form");
        }

        @Test
        void reportingIsADecoratorTheTestCanRecordAndItMasksTheCard() {
            List<String> steps = new ArrayList<>();
            new CartPrecondition(api).fill("alice", CART);
            Browser browser = new ReportingBrowser(new FakeBrowser(shop, Markup.V1, "alice"), steps::add);

            CheckoutPage.open(browser).payWith(Cards.validVisa());

            assertThat(steps).containsExactly(
                    "open /checkout",
                    "type #card = ************1111",
                    "click #pay",
                    "read #order-id = ord-1");
        }

        @Test
        void theOracleIsAPlainFunction() {
            assertThat(ExpectedPrice.of(CART)).isEqualByComparingTo(CART_TOTAL);
            assertThat(ExpectedPrice.of(List.of(Item.of("a", "99.99", 1)))).isEqualByComparingTo("119.99");
            assertThat(ExpectedPrice.of(List.of(Item.of("a", "100.00", 1)))).isEqualByComparingTo("108.00");
        }
    }

    @Nested
    class Overapplied {

        @Test
        void fiveClassesDoWhatOnePageDid() {
            new CartPrecondition(api).fill("alice", CART);
            CheckoutFlow flow = new CheckoutFlow(new FakeBrowser(shop, Markup.V1, "alice")).open();

            assertThat(flow.verify().total()).isEqualByComparingTo(CART_TOTAL);
            assertThat(flow.payWith(Cards.validVisa())).startsWith("ord-");
        }
    }

    private static String captureStdout(Runnable action) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true));
        try {
            action.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString();
    }
}
