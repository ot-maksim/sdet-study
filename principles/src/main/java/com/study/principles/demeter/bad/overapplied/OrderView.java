package com.study.principles.demeter.bad.overapplied;

import com.study.principles.demeter.good.chains.Order;

/**
 * SMELL (the opposite one): LoD applied to data by counting dots. To "avoid" {@code order.customer().address().city()},
 * every leaf of the response gets a forwarding method. The result:
 * <ul>
 *   <li>a mirror of the DTO that must grow with every new field (wide, shallow interface);</li>
 *   <li>nothing hidden — the names still spell out the path ({@code getCustomerAddressCity});</li>
 *   <li>assertions lose structure: no {@code extracting(..)}, no recursive comparison of {@code address}.</li>
 * </ul>
 */
public final class OrderView {

    private final Order order;

    public OrderView(Order order) {
        this.order = order;
    }

    public String getId() {
        return order.id();
    }

    public String getCustomerName() {
        return order.customer().name();
    }

    public String getCustomerAddressCity() {
        return order.customer().address().city();
    }

    public String getCustomerAddressZip() {
        return order.customer().address().zip();
    }

    public String getLineSku(int index) {
        return order.lines().get(index).sku();
    }

    public int getLineQuantity(int index) {
        return order.lines().get(index).quantity();
    }
}
