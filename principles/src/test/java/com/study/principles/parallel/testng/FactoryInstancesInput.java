package com.study.principles.parallel.testng;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

import java.util.concurrent.CyclicBarrier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Input for {@link JUnitVsTestNgLifecycleTest}: {@code @Factory} creates one instance per user, and with
 * {@code parallel="instances"} each instance runs on its own thread with its methods in sequence, so its
 * fields really are per test. Not named {@code *Test}: the normal build does not run it.
 */
public class FactoryInstancesInput {

    static final CyclicBarrier bothRunning = new CyclicBarrier(2);
    private final String user;
    private String driver;

    @Factory(dataProvider = "users")
    public FactoryInstancesInput(String user) {
        this.user = user;
    }

    @DataProvider
    public static Object[][] users() {
        return new Object[][]{{"alice"}, {"bob"}};
    }

    @BeforeMethod
    public void openBrowser() {
        driver = "browser of " + user;
    }

    @Test
    public void profile() {
        JUnitVsTestNgLifecycleTest.await(bothRunning);  // both instances really run at the same time
        assertThat(driver).isEqualTo("browser of " + user);
    }
}
