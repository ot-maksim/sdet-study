package com.study.principles.kiss.flags.good;

import com.study.principles.kiss.flags.Order;

/** An order deliberately left unpaid — the "don't pay" flag as a return type. */
public record PendingOrder(Order order) {
}
