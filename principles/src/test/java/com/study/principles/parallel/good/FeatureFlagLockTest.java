package com.study.principles.parallel.good;

import com.study.principles.parallel.Credentials;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

/**
 * A global switch of the SUT under real parallel execution, three kinds of tests:
 * <ul>
 *   <li>tests that <b>change</b> the global flag: {@code @ResourceLock(.., READ_WRITE)}, run alone
 *       against every other test holding the same lock;</li>
 *   <li>tests that <b>depend</b> on its default: {@code READ}, run in parallel with each other, never
 *       during a writer;</li>
 *   <li>tests that need the feature only for their own user: a per-user override, <b>no lock</b>, run
 *       in parallel with everything.</li>
 * </ul>
 * The counters check the lock instead of trusting it. Remove the two annotations and run the class a few
 * times to see why they are there. Tests that neither change nor read the flag need no lock at all: the
 * lock serializes only what really conflicts, unlike {@code @Isolated} or a separate sequential run.
 */
@ExtendWith(ShopExtension.class)
@Execution(ExecutionMode.CONCURRENT)
class FeatureFlagLockTest {

    private static final String FEATURE_FLAGS = "shop.feature-flags";
    private static final AtomicInteger writers = new AtomicInteger();
    private static final AtomicInteger readers = new AtomicInteger();

    @RepeatedTest(10)
    @ResourceLock(value = FEATURE_FLAGS, mode = READ_WRITE)
    void newProfileForEverybody(ShopEnvironment environment, ShopApi api, ProfilePage profile, TestUsers users)
            throws InterruptedException {
        assertThat(writers.incrementAndGet()).as("writers at once").isEqualTo(1);
        assertThat(readers.get()).as("readers during a write").isZero();
        try {
            Credentials me = users.create();
            environment.admin().setFeature("new-profile", true);
            Thread.sleep(5); // the UI steps between switching and checking: the window for a race

            assertThat(profile.openWithSession(api.login(me)).title()).isEqualTo("Your profile");
        } finally {
            environment.admin().setFeature("new-profile", false);
            writers.decrementAndGet();
        }
    }

    @RepeatedTest(10)
    @ResourceLock(value = FEATURE_FLAGS, mode = READ)
    void oldProfileByDefault(ShopApi api, ProfilePage profile, TestUsers users) throws InterruptedException {
        readers.incrementAndGet();
        try {
            assertThat(writers.get()).as("writers during a read").isZero();
            Credentials me = users.create();
            Thread.sleep(5);

            assertThat(profile.openWithSession(api.login(me)).title()).isEqualTo("Profile");
        } finally {
            readers.decrementAndGet();
        }
    }

    @RepeatedTest(10)
    void newProfileForMeOnly_needsNoLock(ShopEnvironment environment, ShopApi api, ProfilePage profile, TestUsers users)
            throws InterruptedException {
        Credentials me = users.create();
        environment.admin().setFeatureFor("new-profile", me.username(), true);
        Thread.sleep(5);

        assertThat(profile.openWithSession(api.login(me)).title()).isEqualTo("Your profile");
    }
}
