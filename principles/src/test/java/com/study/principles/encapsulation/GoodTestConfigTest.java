package com.study.principles.encapsulation;

import com.study.principles.encapsulation.good.AdminSmokeTest;
import com.study.principles.encapsulation.good.LoginPage;
import com.study.principles.encapsulation.good.TestConfig;
import com.study.principles.encapsulation.good.UsersApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("good: immutable config with an intent-level API")
class GoodTestConfigTest {

    private final FakeTransport transport = new FakeTransport();

    @ParameterizedTest(name = "baseUrl=''{0}''")
    @ValueSource(strings = {"http://localhost:7070", "http://localhost:7070/", "http://localhost:7070//"})
    void bothClientsGetCorrectUrlsWhateverTheSlashes(String baseUrl) {
        TestConfig config = TestConfig.from(Map.of("baseUrl", baseUrl));

        new UsersApiClient(config, transport).getUser(1);
        new LoginPage(config, transport).open();

        assertThat(transport.calls())
                .extracting(FakeTransport.Call::url)
                .containsExactly("http://localhost:7070/api/users/1", "http://localhost:7070/login");
    }

    @Test
    void contextPathNeedsNoChangeInClients() {
        TestConfig config = TestConfig.from(Map.of("baseUrl", "https://stage.example.com/sut"));

        new LoginPage(config, transport).open();

        assertThat(transport.last().url()).isEqualTo("https://stage.example.com/sut/login");
    }

    @Test
    void timeoutHasOneMeaningForEveryone() {
        TestConfig config = TestConfig.from(Map.of("timeout.ms", "5000"));

        new UsersApiClient(config, transport).getUser(1);
        new LoginPage(config, transport).open();

        assertThat(transport.calls())
                .extracting(FakeTransport.Call::timeout)
                .containsOnly(Duration.ofSeconds(5));
    }

    @Test
    void overrideForOneTestDoesNotLeak() {
        TestConfig shared = TestConfig.from(Map.of());

        new AdminSmokeTest(shared, transport).adminCanSeeUser();
        new UsersApiClient(shared, transport).getUser(1);

        assertThat(transport.calls())
                .extracting(FakeTransport.Call::url)
                .containsExactly("http://admin.local:9090/api/users/1", "http://localhost:7070/api/users/1");
    }

    @Test
    void invalidConfigFailsFastWithAllProblemsAtOnce() {
        assertThatThrownBy(() -> TestConfig.from(Map.of("baseUrl", "localhost:7070", "timeout.ms", "-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseUrl 'localhost:7070'")
                .hasMessageContaining("timeout.ms must be > 0");
    }

    @Test
    void invalidOverrideIsRejectedToo() {
        TestConfig config = TestConfig.from(Map.of());

        assertThatThrownBy(() -> config.withBaseUrl("ftp://files.local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http(s)");
    }
}
