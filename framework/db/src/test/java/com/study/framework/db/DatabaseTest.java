package com.study.framework.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.study.framework.core.config.Config;
import com.study.framework.core.config.ConfigException;
import java.net.ServerSocket;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** No database needed. */
class DatabaseTest {

    private final ClassLoader classPath = getClass().getClassLoader();

    @Test
    void passwordHasNoDefaultAndStaysMasked() {
        Map<String, String> noEnvFilePassword = Map.of("db.password", "");   // a blank system property hides the env file's value

        assertThatThrownBy(() -> Config.load(noEnvFilePassword, Map.of(), classPath).bind(DbSettings::from))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("missing db.password");
        DbSettings settings = Config.load(Map.of(), Map.of("DB_PASSWORD", "from-ci"), classPath).bind(DbSettings::from);
        assertThat(settings.password().reveal()).isEqualTo("from-ci");
        assertThat(settings.toString()).doesNotContain("from-ci");
    }

    @Test
    void anUnreachableDatabaseIsOneClearMessage() throws Exception {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }
        String url = "jdbc:postgresql://localhost:" + freePort + "/sut";
        Database db = new Database(Config.load(Map.of("db.url", url), Map.of(), classPath).bind(DbSettings::from));

        assertThatThrownBy(() -> db.query("SELECT 1", rs -> 1))
                .isInstanceOf(DbException.class)
                .hasMessageContaining("Cannot connect to " + url)
                .hasMessageContaining("docker compose up");
    }
}
