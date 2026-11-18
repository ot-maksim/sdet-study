package com.study.principles.lsp.fakes;

import com.study.principles.lsp.fakes.RegistrationService.Result;
import com.study.principles.lsp.fakes.bad.LenientFakeUserRepository;
import com.study.principles.lsp.fakes.good.InMemoryUserRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a contract-breaking fake does to the tests of the code under test: the same scenario gives a different
 * answer on the fake than on the real repository.
 */
class FakeDriftTest {

    @Nested
    class DuplicateRegistration {

        @Test
        void realRepositoryRejectsIt() {
            assertThat(registerTwice(new JdbcLikeUserRepository())).isEqualTo(Result.ALREADY_REGISTERED);
        }

        @Test
        void contractHonouringFakeAgrees() {
            assertThat(registerTwice(new InMemoryUserRepository())).isEqualTo(Result.ALREADY_REGISTERED);
        }

        @Test
        void lenientFakeClaimsTheServiceAcceptsDuplicates() {
            // a team that trusts this fake either "fixes" the service with a racy pre-check, or disables the test
            assertThat(registerTwice(new LenientFakeUserRepository())).isEqualTo(Result.REGISTERED);
        }

        private Result registerTwice(UserRepository users) {
            RegistrationService service = new RegistrationService(users);
            service.register("alice@shop.test", "Alice");
            return service.register("alice@shop.test", "Alice again");
        }
    }

    /** {@link ProfileService#rename} forgets {@code update}. Which repository lets a test see it? */
    @Nested
    class MissingUpdateBug {

        @Test
        void realRepositoryShowsTheRenameWasLost() {
            assertThat(renameAndReadBack(new JdbcLikeUserRepository())).isEqualTo("Alice");
        }

        @Test
        void contractHonouringFakeShowsItToo() {
            assertThat(renameAndReadBack(new InMemoryUserRepository())).isEqualTo("Alice");
        }

        @Test
        void lenientFakeHidesTheBugTheTestIsGreen() {
            assertThat(renameAndReadBack(new LenientFakeUserRepository())).isEqualTo("Alice Smith");
        }

        private String renameAndReadBack(UserRepository users) {
            users.insert(new User("alice@shop.test", "Alice"));
            new ProfileService(users).rename("alice@shop.test", "Alice Smith");
            return users.findByEmail("alice@shop.test").orElseThrow().name();
        }
    }
}
