package com.study.project.db;

import com.study.project.core.Secret;

/** Where the database is. The password stays a {@link Secret} until the driver needs it. */
public record DbSettings(String url, String user, Secret password) {

    public DbSettings {
        if (!url.startsWith("jdbc:postgresql:")) {
            throw new IllegalArgumentException("db url must be a jdbc:postgresql: URL, was " + url);
        }
    }
}
