package com.study.project.environment;

import com.study.project.core.Secret;
import java.net.URI;

/**
 * A running stand ({@code project.stand.*}). The password is a {@link Secret} already at binding: it never sits in
 * a String field of a record that may be logged. {@code kafkaBootstrap} empty: the stand has no broker, event tests
 * are skipped.
 */
public record StandSettings(URI sutUrl, String dbUrl, String dbUser, Secret dbPassword, String kafkaBootstrap) {
}
