package com.study.principles.parallel.testng;

import com.study.principles.parallel.Credentials;
import com.study.principles.parallel.testng.good.SharedResource;
import com.study.principles.parallel.testng.good.ShopNgTestBase;
import org.testng.annotations.AfterClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static com.study.principles.parallel.testng.good.SharedResource.Mode.READ;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The TestNG twin of {@code ShopParallelTest} + {@code FeatureFlagLockTest}, run by the normal build
 * with {@code testng.parallel=methods} (see {@code junit-platform.properties}). Differences to notice:
 * <ul>
 *   <li>per-test objects come from {@code profile()}, {@code users()}: the base class keeps them in the
 *       test's {@code ITestResult}, not in fields (one instance serves all methods here) and not in
 *       parameters (TestNG cannot inject custom types);</li>
 *   <li>repetitions are a {@code @DataProvider(parallel = true)}: all its invocations share this instance,
 *       which is safe only because the class has no mutable fields;</li>
 *   <li>the lock is {@link SharedResource}, enforced by the base class around the test body: a writer
 *       blocks a pool thread (its browser already open) instead of not being started at all.</li>
 * </ul>
 */
public class ShopNgParallelTest extends ShopNgTestBase {

    private static final String FEATURE_FLAGS = "shop.feature-flags";
    private static final AtomicInteger writers = new AtomicInteger();
    private static final AtomicInteger readers = new AtomicInteger();

    @DataProvider(parallel = true)
    public static Object[][] times30() {
        return times(30);
    }

    @DataProvider(parallel = true)
    public static Object[][] times10() {
        return times(10);
    }

    @Test(dataProvider = "times30")
    public void newUserSeesOwnNameViaApiAndOnProfilePage(int run) {
        Credentials me = users().create();

        assertThat(api().as(api().login(me)).withLanguage("de").me().greeting()).isEqualTo("Hallo, " + me.username());
        assertThat(profile().openWithSession(api().login(me)).greeting()).isEqualTo("Hello, " + me.username());
    }

    @Test(dataProvider = "times10")
    public void premiumUserSeesPremiumBadge(int run) {
        Credentials premium = users().leasePremium();

        profile().openWithSession(api().login(premium));

        assertThat(profile().isOpen()).as("still logged in: nobody else used this account").isTrue();
        assertThat(profile().plan()).isEqualTo("PREMIUM");
    }

    @Test(dataProvider = "times10")
    @SharedResource(FEATURE_FLAGS)
    public void newProfileForEverybody(int run) throws InterruptedException {
        assertThat(writers.incrementAndGet()).as("writers at once").isEqualTo(1);
        assertThat(readers.get()).as("readers during a write").isZero();
        try {
            Credentials me = users().create();
            environment().admin().setFeature("new-profile", true);
            Thread.sleep(5);

            assertThat(profile().openWithSession(api().login(me)).title()).isEqualTo("Your profile");
        } finally {
            environment().admin().setFeature("new-profile", false);
            writers.decrementAndGet();
        }
    }

    @Test(dataProvider = "times10")
    @SharedResource(value = FEATURE_FLAGS, mode = READ)
    public void oldProfileByDefault(int run) throws InterruptedException {
        readers.incrementAndGet();
        try {
            assertThat(writers.get()).as("writers during a read").isZero();
            Credentials me = users().create();
            Thread.sleep(5);

            assertThat(profile().openWithSession(api().login(me)).title()).isEqualTo("Profile");
        } finally {
            readers.decrementAndGet();
        }
    }

    @Test(dataProvider = "times10")
    public void newProfileForMeOnly_needsNoLock(int run) throws InterruptedException {
        Credentials me = users().create();
        environment().admin().setFeatureFor("new-profile", me.username(), true);
        Thread.sleep(5);

        assertThat(profile().openWithSession(api().login(me)).title()).isEqualTo("Your profile");
    }

    @AfterClass(alwaysRun = true)
    public void sharedStateWasSharedOnlyWhereIntended() {
        assertThat(environment().adminLogins()).isEqualTo(1);
    }

    private static Object[][] times(int count) {
        Object[][] rows = new Object[count][];
        for (int i = 0; i < count; i++) {
            rows[i] = new Object[]{i + 1};
        }
        return rows;
    }
}
