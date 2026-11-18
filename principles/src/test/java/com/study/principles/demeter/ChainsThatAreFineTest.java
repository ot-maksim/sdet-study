package com.study.principles.demeter;

import com.study.principles.demeter.bad.overapplied.OrderView;
import com.study.principles.demeter.good.chains.NewUser;
import com.study.principles.demeter.good.chains.Order;
import com.study.principles.demeter.good.chains.Order.Address;
import com.study.principles.demeter.good.chains.Order.Customer;
import com.study.principles.demeter.good.chains.Order.Line;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** Long chains that do NOT violate the Law of Demeter, and what "fixing" them looks like. */
@DisplayName("Law of Demeter: chains that are fine")
class ChainsThatAreFineTest {

    private final Order order = new Order("o-1",
            new Customer("Alice", new Address("Berlin", "10115")),
            List.of(new Line("SKU-1", 2), new Line("SKU-2", 1)));

    @Test
    void builderChainTalksToOneFriendRepeatedly() {
        NewUser admin = NewUser.builder().name("Root").email("root@test.io").admin().build();

        assertThat(admin).isEqualTo(new NewUser("Root", "root@test.io", true));
    }

    @Test
    void assertionDslIsAChainByDesign() {
        assertThat(order.lines())
                .extracting(Line::sku, Line::quantity)
                .containsExactly(tuple("SKU-1", 2), tuple("SKU-2", 1));
    }

    @Test
    void navigatingAResponseDtoIsReadingTheContract() {
        assertThat(order.customer().address().city()).isEqualTo("Berlin");
        assertThat(order.customer().address())
                .usingRecursiveComparison()
                .isEqualTo(new Address("Berlin", "10115"));   // whole sub-structure at once
    }

    @Test
    void overappliedLawMirrorsEveryLeafAndHidesNothing() {
        OrderView view = new OrderView(order);

        assertThat(view.getCustomerAddressCity()).isEqualTo("Berlin");   // the path is still in the name
        long forwarders = Arrays.stream(OrderView.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .count();
        assertThat(forwarders)
                .as("one public method per leaf field: id, name, city, zip, sku, quantity")
                .isEqualTo(6);   // add Address.country → a 7th method; the DTO alone would need nothing
    }
}
