package com.study.principles.concurrency.eventual;

/** An order as the read side shows it ({@code GET /orders/{id}}, search results). */
public record OrderView(String id, String customer, Status status) {

    /** Statuses only move forward: a check for "at least PAID" stays true once it is true. */
    public enum Status {
        NEW, PAID, SHIPPED;

        public boolean isAtLeast(Status other) {
            return compareTo(other) >= 0;
        }
    }
}
