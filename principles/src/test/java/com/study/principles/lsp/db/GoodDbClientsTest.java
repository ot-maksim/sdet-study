package com.study.principles.lsp.db;

import com.study.principles.lsp.db.good.DbReader;
import com.study.principles.lsp.db.good.DbWriter;
import com.study.principles.lsp.db.good.PrimaryClient;
import com.study.principles.lsp.db.good.ReplicaClient;
import com.study.principles.lsp.db.good.TestDataCleaner;
import com.study.principles.lsp.db.good.UserChecks;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoodDbClientsTest {

    @Nested
    class Replica implements DbReaderContract {
        @Override
        public DbReader reader(FakeDatabase db) {
            return new ReplicaClient(db);
        }
    }

    @Nested
    class Primary implements DbWriterContract {
        @Override
        public DbWriter writer(FakeDatabase db) {
            return new PrimaryClient(db);
        }
    }

    @Nested
    class InTheFramework {

        private final FakeDatabase stand = new FakeDatabase();

        @Test
        void readOnlyCodeTakesAReaderAndWorksWithBoth() {
            stand.seed("alice@shop.test");

            for (DbReader reader : List.of(new ReplicaClient(stand), new PrimaryClient(stand))) {
                new UserChecks(reader).assertExists("alice@shop.test");
            }
        }

        @Test
        void cleanupTakesAWriterSoAReplicaCannotBeWiredIn() {
            stand.seed("autotest-1@shop.test", "autotest-2@shop.test");
            // new TestDataCleaner(new ReplicaClient(stand))  — does not compile

            assertThat(new TestDataCleaner(new PrimaryClient(stand)).cleanUp("autotest-")).isEqualTo(2);
            assertThat(stand.select("autotest-")).isEmpty();
        }
    }
}
