package com.study.principles.srp;

import com.study.principles.srp.good.CartPrecondition;
import com.study.principles.srp.good.Cards;
import com.study.principles.srp.good.CheckoutPage;
import com.study.principles.srp.good.ExpectedPrice;
import com.study.principles.srp.good.Orders;
import com.study.principles.srp.good.ReportingBrowser;
import com.study.principles.srp.good.StepReporter;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Two tests — a UI checkout and an API + DB check — before and after the front-end redesign (markup V2).
 * Only the UI test should notice the redesign.
 *
 * mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.srp.SrpDemo
 */
public class SrpDemo {

    private static final List<Item> CART = List.of(Item.of("book", "40.00", 2), Item.of("pen", "5.50", 4));

    public static void main(String[] args) {
        for (Markup markup : Markup.values()) {
            System.out.println("=== BAD: all-in-one CheckoutPage, markup " + markup + " ===");
            run("UI checkout     ", () -> {
                FakeShop shop = new FakeShop();
                var page = new com.study.principles.srp.bad.CheckoutPage(
                        new FakeBrowser(shop, markup, "alice"), new ShopApi(shop), new ShopDb(shop));
                page.prepareCart("alice", CART).verifyTotal(CART);
                return page.payWith(page.randomCard());
            });
            run("API + DB check  ", () -> {
                FakeShop shop = new FakeShop();
                ShopApi api = new ShopApi(shop);
                var page = new com.study.principles.srp.bad.CheckoutPage(
                        new FakeBrowser(shop, markup, "alice"), api, new ShopDb(shop));
                page.prepareCart("alice", CART);
                String orderId = api.placeOrder("alice", page.randomCard());
                page.assertOrderInDb(orderId, CART);
                return orderId;
            });

            System.out.println("=== GOOD: one class per reason to change, markup " + markup + " ===");
            run("UI checkout     ", () -> {
                FakeShop shop = new FakeShop();
                new CartPrecondition(new ShopApi(shop)).fill("alice", CART);
                Browser browser = new ReportingBrowser(new FakeBrowser(shop, markup, "alice"), StepReporter.console());
                CheckoutPage page = CheckoutPage.open(browser);
                if (page.total().compareTo(ExpectedPrice.of(CART)) != 0) throw new AssertionError("wrong total");
                return page.payWith(Cards.validVisa());
            });
            run("API + DB check  ", () -> {
                FakeShop shop = new FakeShop();
                ShopApi api = new ShopApi(shop);
                new CartPrecondition(api).fill("alice", CART);
                String orderId = api.placeOrder("alice", Cards.validVisa());
                if (new Orders(new ShopDb(shop)).get(orderId).total().compareTo(ExpectedPrice.of(CART)) != 0) {
                    throw new AssertionError("wrong stored total");
                }
                return orderId;
            });
            System.out.println();
        }
    }

    private static void run(String name, Callable<String> test) {
        try {
            System.out.println("  " + name + " -> PASSED (" + test.call() + ")");
        } catch (Exception | AssertionError e) {
            System.out.println("  " + name + " -> FAILED: " + e.getMessage());
        }
    }
}
