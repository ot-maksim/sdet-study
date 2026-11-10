package com.study.principles.parallel.good;

import com.study.principles.parallel.Credentials;
import com.study.principles.parallel.FakeShop;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Everything the clients need to know about the stand. A record of immutable values
 * ({@code List.copyOf}), so one instance is shared by the whole run without any care.
 */
public record ShopConfig(String baseUri, Credentials admin, List<Credentials> premiumAccounts,
                         String defaultLanguage, Duration leaseTimeout) {

    public static final ShopConfig DEFAULT = new ShopConfig(
            "http://shop.test", FakeShop.ADMIN, FakeShop.PREMIUM_ACCOUNTS, "en", Duration.ofSeconds(10));

    public ShopConfig {
        Objects.requireNonNull(baseUri, "baseUri");
        Objects.requireNonNull(admin, "admin");
        premiumAccounts = List.copyOf(premiumAccounts); // a caller's ArrayList cannot change us later
        Objects.requireNonNull(defaultLanguage, "defaultLanguage");
        Objects.requireNonNull(leaseTimeout, "leaseTimeout");
    }
}
