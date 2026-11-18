package com.study.principles.lsp.fakes;

import com.study.principles.lsp.fakes.bad.LenientFakeUserRepository;
import com.study.principles.lsp.fakes.good.InMemoryUserRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

class UserRepositoryContractTest {

    @Nested
    class JdbcLike implements UserRepositoryContract {
        @Override
        public UserRepository repository() {
            return new JdbcLikeUserRepository();
        }
    }

    @Nested
    class InMemoryFake implements UserRepositoryContract {
        @Override
        public UserRepository repository() {
            return new InMemoryUserRepository();
        }
    }

    /** Characterization: the lenient fake breaks exactly the two clauses it ignores. */
    @Nested
    class LenientFakeAgainstTheContract {

        @Test
        void failsUniquenessAndCopySemantics() {
            run("aSecondUserWithTheSameEmailIsRejected").assertStatistics(stats -> stats.failed(1));
            run("returnedObjectsAreDetachedCopies").assertStatistics(stats -> stats.failed(1));
        }

        @Test
        void passesTheClausesThatAliasingMakesTrivial() {
            run("insertAssignsDistinctIds").assertStatistics(stats -> stats.succeeded(1));
            run("updateStoresTheCurrentState").assertStatistics(stats -> stats.succeeded(1));
        }

        private Events run(String contractMethod) {
            return EngineTestKit.engine("junit-jupiter")
                    .selectors(selectMethod(LenientContract.class, contractMethod))
                    .execute().testEvents();
        }
    }

    static class LenientContract implements UserRepositoryContract {
        @Override
        public UserRepository repository() {
            return new LenientFakeUserRepository();
        }
    }
}
