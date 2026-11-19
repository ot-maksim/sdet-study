package com.study.review.bad.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.study.review.EngineRuns;
import com.study.review.Stand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.engine.TestExecutionResult;

/**
 * The decoys: code in the bad framework that a grep pass flags and that is right. In this package because
 * the switch is package-private. D2 (fluent chains in {@code UserApi}) needs no test — see REVIEW.md.
 */
class DecoysReviewTest {

    @Test
    void d1_theProfileSwitchIsOnePlaceAndFailsFastOnAnUnknownProfile() {
        assertThat(Driver.contextOptions("mobile").isMobile).isTrue();
        assertThatThrownBy(() -> Driver.contextOptions("tablet"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown profile 'tablet', expected desktop or mobile");
    }

    @Test
    void d3_aFailingScreenshotDoesNotReplaceTheTestsFailure() {
        Stand.forBadFramework();
        try {
            Throwable failure = EngineRuns.run(FailsWithAClosedPage.class).testEvents().failed().stream()
                    .map(e -> e.getRequiredPayload(TestExecutionResult.class).getThrowable().orElseThrow())
                    .findFirst().orElseThrow();

            assertThat(failure).isInstanceOf(AssertionError.class).hasMessage("the real failure");
            assertThat(failure.getSuppressed()).as("the screenshot's own failure, kept as evidence").hasSize(1);
        } finally {
            Driver.stop();
        }
    }

    @ExtendWith(ScreenshotOnFailure.class)
    static class FailsWithAClosedPage {
        @Test
        void fails() {
            Driver.start();
            Driver.getPage().close();   // the screenshot will fail
            throw new AssertionError("the real failure");
        }
    }
}
