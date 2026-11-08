package com.study.principles.encapsulation;

import com.study.principles.encapsulation.bad.AdminSmokeTest;
import com.study.principles.encapsulation.bad.LoginPage;
import com.study.principles.encapsulation.bad.TestConfig;
import com.study.principles.encapsulation.bad.UsersApiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization tests: they PASS because they pin down the bugs of the bad design.
 * Note the {@code @AfterEach}: every test of a global mutable config must know its defaults and
 * restore them by hand — and this still breaks the moment tests run in parallel.
 */
@DisplayName("bad: raw values + setters + global state")
class BadTestConfigTest {

    private final FakeTransport transport = new FakeTransport();

    @AfterEach
    void restoreGlobalConfig() {
        TestConfig.get().setBaseUrl("http://localhost:7070/");
        TestConfig.get().setTimeout(5);
    }

    @ParameterizedTest(name = "baseUrl=''{0}''")
    @ValueSource(strings = {"http://localhost:7070", "http://localhost:7070/"})
    void noBaseUrlValueSatisfiesBothClients(String baseUrl) {
        TestConfig.get().setBaseUrl(baseUrl);

        new UsersApiClient(transport).getUser(1);
        new LoginPage(transport).open();

        String apiUrl = transport.calls().get(0).url();
        String pageUrl = transport.calls().get(1).url();
        boolean apiOk = apiUrl.equals("http://localhost:7070/api/users/1");
        boolean pageOk = pageUrl.equals("http://localhost:7070/login");
        assertThat(apiOk && pageOk)
                .as("api=%s, page=%s", apiUrl, pageUrl)
                .isFalse();
    }

    @Test
    void sameIntMeansDifferentDurationsToDifferentClients() {
        TestConfig.get().setTimeout(5000);   // "fix" for the flaky API tests

        new UsersApiClient(transport).getUser(1);
        new LoginPage(transport).open();

        assertThat(transport.calls().get(0).timeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(transport.calls().get(1).timeout()).isEqualTo(Duration.ofSeconds(5000)); // 83 minutes
    }

    @Test
    void oneTestChangingTheHostLeaksIntoTheNext() {
        new AdminSmokeTest(transport).adminCanSeeUser();
        new UsersApiClient(transport).getUser(1);   // "the next test", unaware of the previous one

        assertThat(transport.last().url()).startsWith("http://admin.local:9090");
    }

    @Test
    void anyGarbageIsAccepted() {
        TestConfig.get().setBaseUrl("localhost");
        TestConfig.get().setTimeout(-1);

        assertThat(TestConfig.get().getTimeout()).isNegative();   // fails later, in the transport, far from the cause
    }
}
