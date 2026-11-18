package com.study.principles.lsp.db;

import com.study.principles.lsp.db.good.DbReader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public interface DbReaderContract {

    DbReader reader(FakeDatabase db);

    @Test
    default void usersAreFilteredByPrefix() {
        FakeDatabase db = new FakeDatabase();
        db.seed("autotest-1@shop.test", "autotest-2@shop.test", "real@shop.test");

        assertThat(reader(db).users("autotest-")).containsExactly("autotest-1@shop.test", "autotest-2@shop.test");
    }
}
