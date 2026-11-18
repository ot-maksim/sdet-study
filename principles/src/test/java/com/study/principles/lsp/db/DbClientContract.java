package com.study.principles.lsp.db;

import com.study.principles.lsp.db.bad.DbClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@link DbClient} contract as a JUnit test interface. A subclass that passes it is substitutable; one that
 * does not is an LSP violation, whatever its name says. Implement it to check a new client.
 */
public interface DbClientContract {

    DbClient client(FakeDatabase db);

    @Test
    default void usersAreFilteredByPrefix() {
        FakeDatabase db = new FakeDatabase();
        db.seed("autotest-1@shop.test", "autotest-2@shop.test", "real@shop.test");

        assertThat(client(db).users("autotest-")).containsExactly("autotest-1@shop.test", "autotest-2@shop.test");
    }

    @Test
    default void anInsertedUserIsVisible() {
        DbClient client = client(new FakeDatabase());

        client.insertUser("autotest-1@shop.test");

        assertThat(client.users("autotest-")).containsExactly("autotest-1@shop.test");
    }

    @Test
    default void deleteLeavesNoUserWithThePrefixAndCountsThem() {
        FakeDatabase db = new FakeDatabase();
        db.seed("autotest-1@shop.test", "autotest-2@shop.test", "real@shop.test");
        DbClient client = client(db);

        assertThat(client.deleteUsers("autotest-")).isEqualTo(2);
        assertThat(client.users("")).containsExactly("real@shop.test");
    }
}
