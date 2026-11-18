package com.study.principles.srp;

import com.study.principles.srp.good.CartPrecondition;
import com.study.principles.srp.good.ExpectedPrice;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a separate oracle buys: 1000 carts checked against the SUT's API in milliseconds, no browser.
 * With the rule locked inside {@code bad.CheckoutPage} each cart would cost a page load.
 */
class ExpectedPriceProperties {

    @Property(tries = 1000)
    void oracleAgreesWithTheShopForAnyCart(@ForAll @Size(min = 1, max = 5) List<@From("items") Item> cart) {
        ShopApi api = new ShopApi(new FakeShop());
        new CartPrecondition(api).fill("alice", cart);

        assertThat(api.cartTotal("alice")).isEqualByComparingTo(ExpectedPrice.of(cart));
    }

    @Provide
    Arbitrary<Item> items() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofLength(4),
                Arbitraries.bigDecimals().between(new BigDecimal("0.01"), new BigDecimal("500.00")).ofScale(2),
                Arbitraries.integers().between(1, 5)
        ).as(Item::new);
    }
}
