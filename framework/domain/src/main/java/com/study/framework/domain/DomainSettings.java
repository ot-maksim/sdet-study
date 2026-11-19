package com.study.framework.domain;

import com.study.framework.core.config.ConfigReader;
import java.util.Set;

/** {@code domain.user-precondition}: {@code api} or {@code db}. */
public record DomainSettings(String userPrecondition) {

    private static final Set<String> CHANNELS = Set.of("api", "db");

    public DomainSettings {
        if (!CHANNELS.contains(userPrecondition)) {
            throw new IllegalArgumentException("domain.user-precondition must be one of " + CHANNELS + ", was " + userPrecondition);
        }
    }

    public static DomainSettings from(ConfigReader config) {
        return new DomainSettings(config.string("domain.user-precondition"));
    }
}
