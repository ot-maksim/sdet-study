package com.study.principles.srp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The SUT backend: carts, pricing and orders. Pricing rule (the product team's reason to change):
 * subtotal, minus 10% when the subtotal is at least 100, plus 20% VAT, rounded half-up to cents at the end.
 */
public final class FakeShop {

    public record Order(String id, String user, BigDecimal total, String cardLast4) {
    }

    private final Map<String, List<Item>> carts = new ConcurrentHashMap<>();
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    public void addToCart(String user, Item item) {
        carts.computeIfAbsent(user, key -> new ArrayList<>()).add(item);
    }

    public BigDecimal total(String user) {
        BigDecimal subtotal = carts.getOrDefault(user, List.of()).stream()
                .map(item -> item.price().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (subtotal.compareTo(new BigDecimal("100")) >= 0) subtotal = subtotal.multiply(new BigDecimal("0.9"));
        return subtotal.multiply(new BigDecimal("1.2")).setScale(2, RoundingMode.HALF_UP);
    }

    public String placeOrder(String user, String card) {
        if (!card.matches("\\d{16}")) throw new IllegalArgumentException("invalid card");
        String id = "ord-" + ids.incrementAndGet();
        orders.put(id, new Order(id, user, total(user), card.substring(12)));
        carts.remove(user);
        return id;
    }

    public Optional<Order> order(String id) {
        return Optional.ofNullable(orders.get(id));
    }
}
