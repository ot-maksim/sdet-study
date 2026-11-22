package com.study.project.environment;

import com.study.project.core.Secret;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * Where the system under test is: everything the clients need to reach it, whoever started it.
 * Clients are built from this, never from a provider-specific key, so tests do not know which provider ran.
 * {@code kafkaBootstrap} is empty when the environment has no broker (a stand without events).
 */
public record SutEnvironment(String description, URI sutUrl, String dbUrl, String dbUser, Secret dbPassword,
                             Optional<String> kafkaBootstrap) {

    public SutEnvironment {
        Objects.requireNonNull(kafkaBootstrap, "kafkaBootstrap");
    }

    @Override
    public String toString() {
        return description + " (SUT " + sutUrl + ", DB " + dbUrl + " as " + dbUser
                + kafkaBootstrap.map(k -> ", Kafka " + k).orElse(", no Kafka") + ")";
    }
}
