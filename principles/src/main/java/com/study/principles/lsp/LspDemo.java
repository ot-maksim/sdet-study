package com.study.principles.lsp;

import com.study.principles.lsp.db.FakeDatabase;
import com.study.principles.lsp.db.bad.DbClient;
import com.study.principles.lsp.db.bad.ReadOnlyDbClient;
import com.study.principles.lsp.db.bad.SilentReadOnlyDbClient;
import com.study.principles.lsp.db.bad.TestDataCleaner;
import com.study.principles.lsp.fakes.JdbcLikeUserRepository;
import com.study.principles.lsp.fakes.ProfileService;
import com.study.principles.lsp.fakes.RegistrationService;
import com.study.principles.lsp.fakes.User;
import com.study.principles.lsp.fakes.UserRepository;
import com.study.principles.lsp.fakes.bad.LenientFakeUserRepository;
import com.study.principles.lsp.fakes.good.InMemoryUserRepository;

import java.util.Map;
import java.util.function.Function;

/**
 * Part 1: the same @AfterEach cleanup with three "DbClient"s. Part 2: the same two scenarios on the real
 * repository and two fakes.
 *
 * mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.lsp.LspDemo
 */
public class LspDemo {

    public static void main(String[] args) {
        System.out.println("=== db: TestDataCleaner(DbClient).cleanUp(\"autotest-\") ===");
        Map<String, Function<FakeDatabase, DbClient>> clients = Map.of(
                "DbClient              ", DbClient::new,
                "ReadOnlyDbClient      ", ReadOnlyDbClient::new,
                "SilentReadOnlyDbClient", SilentReadOnlyDbClient::new);
        clients.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            FakeDatabase stand = new FakeDatabase();
            stand.seed("autotest-1@shop.test", "autotest-2@shop.test");
            String outcome;
            try {
                int removed = new TestDataCleaner(entry.getValue().apply(stand)).cleanUp("autotest-");
                outcome = "removed " + removed;
            } catch (RuntimeException e) {
                outcome = "THROWS " + e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            System.out.println("  " + entry.getKey() + " -> " + outcome + "; left in DB: " + stand.select("autotest-"));
        });

        System.out.println();
        System.out.println("=== fakes: same scenarios, three repositories ===");
        Map<String, java.util.function.Supplier<UserRepository>> repositories = Map.of(
                "1 JdbcLike (real)  ", JdbcLikeUserRepository::new,
                "2 InMemory (good)  ", InMemoryUserRepository::new,
                "3 Lenient (bad)    ", LenientFakeUserRepository::new);
        repositories.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            RegistrationService registration = new RegistrationService(entry.getValue().get());
            registration.register("alice@shop.test", "Alice");
            RegistrationService.Result second = registration.register("alice@shop.test", "Alice again");

            UserRepository users = entry.getValue().get();
            users.insert(new User("alice@shop.test", "Alice"));
            new ProfileService(users).rename("alice@shop.test", "Alice Smith");   // forgets update()
            String name = users.findByEmail("alice@shop.test").orElseThrow().name();

            System.out.println("  " + entry.getKey() + " second registration -> " + second
                    + "; name after buggy rename -> " + name);
        });
    }
}
