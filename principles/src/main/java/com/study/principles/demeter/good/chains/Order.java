package com.study.principles.demeter.good.chains;

import java.util.List;

/**
 * NOT a violation: a response DTO is a <i>data structure</i>, not an object with behaviour. Its shape is
 * the API contract, published on purpose, so {@code order.customer().address().city()} in an assertion
 * navigates data the test is supposed to know. LoD protects hidden behaviour; there is none here.
 * <p>
 * If the contract changes ({@code address} moves), tests asserting on it <i>should</i> change.
 */
public record Order(String id, Customer customer, List<Line> lines) {

    public Order {
        lines = List.copyOf(lines);
    }

    public record Customer(String name, Address address) {
    }

    public record Address(String city, String zip) {
    }

    public record Line(String sku, int quantity) {
    }
}
