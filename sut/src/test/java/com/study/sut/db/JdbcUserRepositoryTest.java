package com.study.sut.db;

import com.study.sut.users.UserRepository;
import com.study.sut.users.UserRepositoryContract;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * The same contract against Postgres. Runs only with a database at hand, e.g. after {@code docker compose up -d postgres}:
 * {@code SUT_TEST_DB_URL=jdbc:postgresql://localhost:5432/sut mvn -q -pl sut test}.
 */
@EnabledIfEnvironmentVariable(named = "SUT_TEST_DB_URL", matches = ".+")
class JdbcUserRepositoryTest implements UserRepositoryContract {

    private static PGSimpleDataSource dataSource;

    @BeforeAll
    static void connect() {
        dataSource = new PGSimpleDataSource();
        dataSource.setUrl(System.getenv("SUT_TEST_DB_URL"));
        dataSource.setUser(System.getenv().getOrDefault("SUT_DB_USER", "sut"));
        dataSource.setPassword(System.getenv().getOrDefault("SUT_DB_PASSWORD", "sut"));
        Schema.apply(dataSource);
    }

    @Override
    public UserRepository repository() {
        return new JdbcUserRepository(dataSource);
    }
}
