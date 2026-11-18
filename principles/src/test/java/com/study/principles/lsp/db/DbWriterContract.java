package com.study.principles.lsp.db;

import com.study.principles.lsp.db.good.DbReader;
import com.study.principles.lsp.db.good.DbWriter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A writer must pass the reader contract too (it extends it) — LSP between the two interfaces, checked. */
public interface DbWriterContract extends DbReaderContract {

    DbWriter writer(FakeDatabase db);

    @Override
    default DbReader reader(FakeDatabase db) {
        return writer(db);
    }

    @Test
    default void anInsertedUserIsVisibleAndADuplicateFails() {
        DbWriter writer = writer(new FakeDatabase());

        writer.insertUser("autotest-1@shop.test");

        assertThat(writer.users("autotest-")).containsExactly("autotest-1@shop.test");
        assertThatThrownBy(() -> writer.insertUser("autotest-1@shop.test")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    default void deleteLeavesNoUserWithThePrefixAndCountsThem() {
        FakeDatabase db = new FakeDatabase();
        db.seed("autotest-1@shop.test", "autotest-2@shop.test", "real@shop.test");
        DbWriter writer = writer(db);

        assertThat(writer.deleteUsers("autotest-")).isEqualTo(2);
        assertThat(writer.users("")).containsExactly("real@shop.test");
    }
}
