package com.study.framework.ui.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import com.study.framework.core.data.Unique;
import com.study.framework.core.lifecycle.Cleanup;
import com.study.framework.ui.BrowserPool;
import com.study.framework.ui.BrowserProfiles;
import com.study.framework.ui.UiSession;
import com.study.framework.ui.UiSettings;
import com.study.framework.ui.UiTestRoot;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.jupiter.api.Test;

/**
 * Runs UI tests concurrently (JUnit parallel execution, 4 threads) against one SUT and one browser pool and
 * checks that they really overlapped, stayed within the pool's limit and all passed. Each test creates and
 * finds its own user by email while the others add rows to the same list.
 */
class ParallelUiTest {

    @ExtendWith(UiTestRoot.class)
    static class ManyUsers {
        static volatile BrowserPool pool;

        @RepeatedTest(8)
        void createAndDeleteOwnUser(RepetitionInfo repetition, BrowserPool browsers, UiSettings settings, Cleanup cleanup, Unique unique) {
            pool = browsers;
            String profile = repetition.getCurrentRepetition() % 2 == 0 ? "desktop" : "mobile";
            UsersPage page = new UsersPage(cleanup.own(new UiSession(browsers, BrowserProfiles.get(profile), settings)).page()).open();
            UserForm form = new UserForm(unique.name("Ada"), unique.email("ada"), "30", "DE");

            ShownUser user = page.create(form);
            assertThat(page.open().find(user.email())).contains(user);
            page.delete(user.email());
            assertThat(page.find(user.email())).isEmpty();
        }
    }

    @Test
    void uiTestsRunInParallelWithABrowserEach() {
        var results = EngineTestKit.engine("junit-jupiter")
                .configurationParameter("junit.jupiter.execution.parallel.enabled", "true")
                .configurationParameter("junit.jupiter.execution.parallel.mode.default", "concurrent")
                .configurationParameter("junit.jupiter.execution.parallel.config.strategy", "fixed")
                .configurationParameter("junit.jupiter.execution.parallel.config.fixed.parallelism", "4")
                .selectors(selectClass(ManyUsers.class))
                .execute();

        List<String> failures = results.testEvents().failed().stream()
                .map(e -> e.getPayload(TestExecutionResult.class).orElseThrow().getThrowable().orElseThrow())
                .map(t -> t.getClass().getSimpleName() + ": " + t.getMessage().lines().limit(12).collect(Collectors.joining("\n    ")))
                .toList();
        assertThat(failures).as("failures of the parallel tests").isEmpty();
        results.testEvents().assertStatistics(s -> s.succeeded(8));
        assertThat(ManyUsers.pool.maxInUse()).as("tests overlapped").isGreaterThan(1);
        assertThat(ManyUsers.pool.launched()).as("never more browsers than ui.max-browsers").isBetween(2, 4);
    }
}
