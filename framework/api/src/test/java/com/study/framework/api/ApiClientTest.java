package com.study.framework.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.study.framework.core.config.Config;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiClientTest {

    @Test
    void settingsComeFromTheModulesDefaultsAndCanBeOverridden() {
        ClassLoader classPath = getClass().getClassLoader();

        ApiSettings defaults = Config.load(Map.of(), Map.of(), classPath).bind(ApiSettings::from);
        ApiSettings overridden = Config.load(Map.of(), Map.of("API_BASE_URL", "https://qa.example.test"), classPath).bind(ApiSettings::from);

        assertThat(defaults).isEqualTo(new ApiSettings(URI.create("http://localhost:7070"), Duration.ofSeconds(5)));
        assertThat(overridden.baseUrl()).isEqualTo(URI.create("https://qa.example.test"));
    }

    @Test
    void anUnreachableStandIsOneClearMessage() throws Exception {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }
        ApiClient client = new ApiClient(new ApiSettings(URI.create("http://localhost:" + freePort), Duration.ofSeconds(2)));

        assertThatThrownBy(() -> client.get("/health"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Cannot reach the SUT at http://localhost:" + freePort + " (GET /health)")
                .hasMessageContaining("docker compose up");
    }

    @Test
    void responseKeepsTheRequestForMessages() {
        try (InProcessSut sut = new InProcessSut()) {
            ApiResponse response = new ApiClient(sut.settings()).postRaw("/api/users", "{\"age\":17}");

            assertThat(response.toString()).isEqualTo(
                    "  POST /api/users {\"age\":17}\n"
                    + "  -> 400 {\"error\":\"name_blank\",\"message\":\"Name must not be blank\"}");
            assertThat(response.error()).isEqualTo(new ApiError("name_blank", "Name must not be blank"));
        }
    }
}
