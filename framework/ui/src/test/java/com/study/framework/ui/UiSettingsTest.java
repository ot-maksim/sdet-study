package com.study.framework.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.study.framework.core.config.Config;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UiSettingsTest {

    @Test
    void defaultsComeFromTheModulesFile() {
        UiSettings settings = Config.load(Map.of(), Map.of("UI_PROFILE", "mobile"), getClass().getClassLoader()).bind(UiSettings::from);

        assertThat(settings).isEqualTo(new UiSettings(URI.create("http://localhost:7070"), "mobile", true, Duration.ofSeconds(5), 4));
    }

    @Test
    void anUnknownProfileListsTheKnownOnes() {
        assertThatThrownBy(() -> BrowserProfiles.get("tablet"))
                .hasMessage("Unknown ui.profile 'tablet'; known: [desktop, mobile]");
    }
}
