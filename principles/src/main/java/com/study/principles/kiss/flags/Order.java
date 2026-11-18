package com.study.principles.kiss.flags;

/** A snapshot of an order in the shop. {@code customer} is {@code null} for a guest order. */
public record Order(long id, String customer, int total, Status status, String paidWith) {

    public enum Status { PENDING, PAID }

    /** The same snapshot without the generated id — to compare runs against different shops. */
    public Order withoutId() {
        return new Order(0, customer, total, status, paidWith);
    }
}
