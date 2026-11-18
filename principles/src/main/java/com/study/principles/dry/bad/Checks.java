package com.study.principles.dry.bad;

import com.study.principles.dry.HttpResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Shared checks, written once. */
public final class Checks {

    private Checks() {
    }

    /** Compares the whole response with what was sent: one line covers every field. */
    public static void verifyUser(HttpResponse response, Map<String, Object> sent) {
        Map<String, Object> actual = new HashMap<>(response.json());
        actual.remove("id");
        actual.remove("role");
        actual.remove("status");
        assertThat(actual.equals(sent)).isTrue();
    }

    /**
     * Users and companies "both validate", so one helper serves both. Then the companies team moved to 422 and
     * a list of errors: the helper grew a flag, and each caller now has to know which branch it wants.
     */
    @SuppressWarnings("unchecked")
    public static void assertRejected(HttpResponse response, String code, boolean company) {
        if (company) {
            assertThat(response.status()).isEqualTo(422);
            assertThat((List<String>) response.json().get("errors")).contains(code);
        } else {
            assertThat(response.status()).isEqualTo(400);
            assertThat(response.json().get("error")).isEqualTo(code);
        }
    }
}
