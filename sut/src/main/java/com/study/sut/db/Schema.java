package com.study.sut.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

/** Applies {@code schema.sql} from the classpath. */
public final class Schema {

    private Schema() {
    }

    public static void apply(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(read("/schema.sql"));
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot apply schema", e);
        }
    }

    private static String read(String resource) {
        try (InputStream in = Schema.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
