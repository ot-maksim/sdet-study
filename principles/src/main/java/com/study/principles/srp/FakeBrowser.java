package com.study.principles.srp;

import java.util.NoSuchElementException;

/**
 * One user's browser on {@code /checkout}, rendered with a given {@link Markup}. Selectors of the other
 * markup version do not exist — like a locator that times out after a redesign.
 */
public final class FakeBrowser implements Browser {

    private final FakeShop shop;
    private final Markup markup;
    private final String user;
    private String path = "about:blank";
    private String card = "";
    private String orderId;

    public FakeBrowser(FakeShop shop, Markup markup, String user) {
        this.shop = shop;
        this.markup = markup;
        this.user = user;
    }

    @Override
    public void open(String path) {
        this.path = path;
        card = "";
        orderId = null;
    }

    @Override
    public boolean isVisible(String selector) {
        if (!path.equals("/checkout")) return false;
        if (selector.equals(markup.orderId)) return orderId != null;
        return selector.equals(markup.form) || selector.equals(markup.card)
                || selector.equals(markup.pay) || selector.equals(markup.total);
    }

    @Override
    public void type(String selector, String text) {
        ensureVisible(selector);
        if (!selector.equals(markup.card)) throw new IllegalArgumentException("not an input: " + selector);
        card = text;
    }

    @Override
    public void click(String selector) {
        ensureVisible(selector);
        if (!selector.equals(markup.pay)) throw new IllegalArgumentException("not a button: " + selector);
        orderId = shop.placeOrder(user, card);
    }

    @Override
    public String text(String selector) {
        ensureVisible(selector);
        if (selector.equals(markup.total)) return shop.total(user).toPlainString();
        if (selector.equals(markup.orderId)) return orderId;
        throw new IllegalArgumentException("no text: " + selector);
    }

    private void ensureVisible(String selector) {
        if (!isVisible(selector)) {
            throw new NoSuchElementException(selector + " not found on " + path + " (markup " + markup + ")");
        }
    }
}
