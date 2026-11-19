package com.study.sut.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class UsersApiTest {

    private final RunningSut sut = new RunningSut();
    private final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void stop() {
        sut.close();
    }

    @Test
    void createsAUser() throws Exception {
        HttpResponse<String> response = sut.postJson("/api/users",
                "{\"name\":\"Ada\",\"email\":\"Ada@Example.com\",\"age\":36,\"country\":\"gb\",\"nickname\":\"ignored\"}");

        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode user = json.readTree(response.body());
        assertThat(response.headers().firstValue("Location")).contains("/api/users/" + user.get("id").asLong());
        assertThat(user.get("name").asText()).isEqualTo("Ada");
        assertThat(user.get("email").asText()).isEqualTo("ada@example.com");
        assertThat(user.get("country").asText()).isEqualTo("GB");
        assertThat(user.get("createdAt").asText()).isEqualTo("2026-11-19T10:15:30.123456Z");

        assertThat(sut.get("/api/users/" + user.get("id").asLong()).body()).isEqualTo(response.body());
    }

    @Test
    void rejectsInvalidUserWithACode() throws Exception {
        HttpResponse<String> response = sut.postJson("/api/users", "{\"name\":\"Kid\",\"email\":\"kid@example.com\",\"age\":17,\"country\":\"DE\"}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json.readTree(response.body()).get("error").asText()).isEqualTo("age_too_low");
        assertThat(json.readTree(sut.get("/api/users").body())).isEmpty();
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        for (String body : new String[] {"{not json", "", "{\"age\":\"old\"}"}) {
            HttpResponse<String> response = sut.postJson("/api/users", body);

            assertThat(response.statusCode()).as(body).isEqualTo(400);
            assertThat(json.readTree(response.body()).get("error").asText()).as(body).isEqualTo("malformed_json");
        }
    }

    @Test
    void emailIsUniqueIgnoringCase() throws Exception {
        sut.postJson("/api/users", "{\"name\":\"Ada\",\"email\":\"ada@example.com\",\"age\":36,\"country\":\"GB\"}");

        HttpResponse<String> response = sut.postJson("/api/users", "{\"name\":\"Ada 2\",\"email\":\"ADA@example.com\",\"age\":40,\"country\":\"GB\"}");

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(json.readTree(response.body()).get("error").asText()).isEqualTo("email_taken");
    }

    @Test
    void findsByEmailAndDeletes() throws Exception {
        long id = json.readTree(sut.postJson("/api/users",
                "{\"name\":\"Ada\",\"email\":\"ada@example.com\",\"age\":36,\"country\":\"GB\"}").body()).get("id").asLong();

        assertThat(json.readTree(sut.get("/api/users?email=ADA@example.com").body())).hasSize(1);
        assertThat(json.readTree(sut.get("/api/users?email=nobody@example.com").body())).isEmpty();

        assertThat(sut.delete("/api/users/" + id).statusCode()).isEqualTo(204);
        HttpResponse<String> gone = sut.get("/api/users/" + id);
        assertThat(gone.statusCode()).isEqualTo(404);
        assertThat(json.readTree(gone.body()).get("error").asText()).isEqualTo("user_not_found");
        assertThat(sut.delete("/api/users/" + id).statusCode()).isEqualTo(404);
        assertThat(sut.get("/api/users/abc").statusCode()).isEqualTo(400);
    }

    @Test
    void health() {
        assertThat(sut.get("/health").body()).isEqualTo("{\"status\":\"UP\"}");
    }
}
