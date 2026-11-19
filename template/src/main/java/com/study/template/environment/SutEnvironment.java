package com.study.template.environment;

import com.study.framework.core.config.Secret;
import java.net.URI;

/**
 * Where the system under test is: everything the clients need to reach it, whoever started it.
 * Clients are built from this, never from a provider-specific key, so tests do not know which provider ran.
 */
public record SutEnvironment(String description, URI sutUrl, String dbUrl, String dbUser, Secret dbPassword) {

    @Override
    public String toString() {
        return description + " (SUT " + sutUrl + ", DB " + dbUrl + " as " + dbUser + ")";
    }
}
