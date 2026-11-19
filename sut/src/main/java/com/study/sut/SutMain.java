package com.study.sut;

import com.study.sut.db.JdbcUserRepository;
import com.study.sut.db.Schema;
import com.study.sut.users.InMemoryUserRepository;
import com.study.sut.users.UserRepository;
import com.study.sut.users.UserService;
import java.time.Clock;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * Entry point; configuration comes from environment variables only.
 *
 * <pre>
 * SUT_PORT         7070
 * SUT_STORAGE      postgres | memory (no database needed)
 * SUT_DB_URL       jdbc:postgresql://localhost:5432/sut
 * SUT_DB_USER      sut
 * SUT_DB_PASSWORD  sut
 * </pre>
 */
public final class SutMain {

    private SutMain() {
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(env("SUT_PORT", "7070"));
        SutApp.create(new UserService(repository(), Clock.systemUTC())).start(port);
    }

    private static UserRepository repository() {
        String storage = env("SUT_STORAGE", "postgres");
        switch (storage) {
            case "memory":
                return new InMemoryUserRepository();
            case "postgres":
                PGSimpleDataSource dataSource = new PGSimpleDataSource();
                dataSource.setUrl(env("SUT_DB_URL", "jdbc:postgresql://localhost:5432/sut"));
                dataSource.setUser(env("SUT_DB_USER", "sut"));
                dataSource.setPassword(env("SUT_DB_PASSWORD", "sut"));
                Schema.apply(dataSource);
                return new JdbcUserRepository(dataSource);
            default:
                throw new IllegalArgumentException("SUT_STORAGE must be postgres or memory, was: " + storage);
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
