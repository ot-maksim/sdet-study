package com.study.principles.kiss.flags.good;

import com.study.principles.kiss.flags.Order;

/** An order the steps paid for. The type says so; whether the shop agrees is for the test to assert. */
public record PaidOrder(Order order) {
}
