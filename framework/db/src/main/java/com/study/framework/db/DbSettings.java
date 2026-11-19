package com.study.framework.db;

import com.study.framework.core.config.ConfigReader;
import com.study.framework.core.config.Secret;

/** The db module's keys: {@code config.bind(DbSettings::from)}. The password stays a {@link Secret} until the driver needs it. */
public record DbSettings(String url, String user, Secret password) {

    public DbSettings {
        if (!url.startsWith("jdbc:postgresql:")) {
            throw new IllegalArgumentException("db.url must be a jdbc:postgresql: URL, was " + url);
        }
    }

    public static DbSettings from(ConfigReader config) {
        return new DbSettings(config.string("db.url"), config.string("db.user"), config.secret("db.password"));
    }
}
