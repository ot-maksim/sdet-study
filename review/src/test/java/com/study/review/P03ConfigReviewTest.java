package com.study.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.study.review.bad.config.FrameworkConfig;
import com.study.review.good.config.Settings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** #3 — a configuration singleton: silent defaults, values frozen at first use, secrets in the log. */
class P03ConfigReviewTest {

    @Nested
    class Bad {

        @Test
        void theTimeoutKeyInTheFileIsNotTheKeyTheCodeReads() throws IOException {
            Properties file = new Properties();
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.properties")) {
                file.load(in);
            }
            Stand.forBadFramework();

            assertThat(file).containsEntry("ui.timeout.ms", "10000").doesNotContainKey("ui.timeout");
            // Driver reads "ui.timeout": the file's 10 s never apply, every wait is 30 s, nobody is told
            assertThat(FrameworkConfig.getInstance().getInt("ui.timeout", 30_000)).isEqualTo(30_000);
        }

        @Test
        void anOverrideAfterTheFirstUseIsIgnored() {
            Stand.forBadFramework();
            System.setProperty("headless", "false");
            try {
                assertThat(FrameworkConfig.getInstance().get("headless")).as("-Dheadless=false set later").isEqualTo("true");
            } finally {
                System.clearProperty("headless");
            }
        }

        @Test
        void thePasswordGoesToTheLog() {
            Stand.forBadFramework();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PrintStream console = System.out;
            System.setOut(new PrintStream(out, true));
            try {
                FrameworkConfig.getInstance().print();
            } finally {
                System.setOut(console);
            }

            assertThat(out.toString()).contains("db.password = sut");
        }
    }

    @Nested
    class Good {

        @Test
        void aTypoFailsAtStartWithEveryProblemAndTheKnownKeys() {
            assertThatThrownBy(() -> Settings.from(Map.of("timeout", "10000", "db.url", "jdbc:postgresql://db/sut")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknown key 'timeout'")
                    .hasMessageContaining("timeout.ms")
                    .hasMessageContaining("'base.url' is required")
                    .hasMessageContaining("database needs all of");
        }

        @Test
        void theSecretIsMaskedWhereverSettingsArePrinted() {
            Settings settings = Settings.from(Map.of("base.url", "http://stand", "db.url", "jdbc:postgresql://db/sut",
                    "db.user", "sut", "db.password", "s3cr3t"));

            assertThat(settings.toString()).doesNotContain("s3cr3t").contains("****");
            assertThat(settings.db().orElseThrow().password().value()).isEqualTo("s3cr3t");
        }

        @Test
        void settingsAreAValueSoATestCanHaveItsOwn() {
            Settings a = Settings.from(Map.of("base.url", "http://a"));
            Settings b = Settings.from(Map.of("base.url", "http://b", "timeout.ms", "500"));

            assertThat(a.baseUrl()).hasToString("http://a");
            assertThat(b.timeout()).hasMillis(500);
        }
    }
}
