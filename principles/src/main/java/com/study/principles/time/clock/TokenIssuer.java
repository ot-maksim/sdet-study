package com.study.principles.time.clock;

/** The auth server's token endpoint, as the framework sees it. */
@FunctionalInterface
public interface TokenIssuer {

    AccessToken issue();
}
