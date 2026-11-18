package com.study.principles.dry.wet;

import com.study.principles.dry.Deployed;
import com.study.principles.dry.HttpResponse;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The opposite extreme: every test spells out the request, the endpoint and the parsing. Each test reads on its
 * own — but the story (one field differs) drowns in mechanics, and an endpoint rename is an edit per test.
 * Input for {@code DryTest}.
 */
public class WetScenarios {

    @Test
    void adultIsAccepted() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "John Doe");
        body.put("email", "john.doe@test.com");
        body.put("age", 18);
        body.put("country", "DE");
        HttpResponse response = Deployed.http().post("/api/v1/users", body);
        assertThat(response.status()).isEqualTo(201);
        assertThat(response.json().get("age")).isEqualTo(18);
    }

    @Test
    void minorIsRejected() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "John Doe");
        body.put("email", "john.doe@test.com");
        body.put("age", 17);
        body.put("country", "DE");
        HttpResponse response = Deployed.http().post("/api/v1/users", body);
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.json().get("error")).isEqualTo("age_too_low");
    }

    @Test
    void nameIsRequired() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "");
        body.put("email", "john.doe@test.com");
        body.put("age", 30);
        body.put("country", "DE");
        HttpResponse response = Deployed.http().post("/api/v1/users", body);
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.json().get("error")).isEqualTo("name_required");
    }

    @Test
    void countryIsStored() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "John Doe");
        body.put("email", "john.doe@test.com");
        body.put("age", 30);
        body.put("country", "DE");
        HttpResponse response = Deployed.http().post("/api/v1/users", body);
        assertThat(response.status()).isEqualTo(201);
        assertThat(response.json().get("country")).isEqualTo("DE");
    }
}
