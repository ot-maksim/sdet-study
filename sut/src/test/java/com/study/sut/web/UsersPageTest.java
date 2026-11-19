package com.study.sut.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** HTML checked as text here; real browser behaviour (mobile layout, toggle) is the framework's job. */
class UsersPageTest {

    private final RunningSut sut = new RunningSut();

    @AfterEach
    void stop() {
        sut.close();
    }

    @Test
    void emptyPage() {
        HttpResponse<String> page = sut.get("/users");

        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.headers().firstValue("Content-Type")).hasValueSatisfying(t -> assertThat(t).startsWith("text/html"));
        assertThat(page.body()).contains("data-testid=\"no-users\"");
    }

    @Test
    void submitRedirectsAndListsTheUserEscaped() {
        HttpResponse<String> submit = sut.postForm("/users",
                Map.of("name", "<b>Bob</b> & Co", "email", "bob@example.com", "age", "40", "country", "de"));

        assertThat(submit.statusCode()).isEqualTo(303);
        assertThat(submit.headers().firstValue("Location")).contains("/users?created=1");

        String page = sut.get("/users?created=1").body();
        assertThat(page)
                .contains("data-testid=\"notice\">User created<")
                .contains("data-user-id=\"1\"")
                .contains("&lt;b&gt;Bob&lt;/b&gt; &amp; Co")
                .doesNotContain("<b>Bob</b>")
                .contains(">DE<");
    }

    @Test
    void rejectedSubmitKeepsTheInputAndShowsTheError() {
        HttpResponse<String> page = sut.postForm("/users",
                Map.of("name", "Kid", "email", "kid@example.com", "age", "17", "country", "DE"));

        assertThat(page.statusCode()).isEqualTo(400);
        assertThat(page.body())
                .contains("data-testid=\"form-error\" data-error-code=\"age_too_low\"")
                .contains("value=\"kid@example.com\"")
                .contains("data-keep-open");
    }

    @Test
    void nonNumericAgeIsAFormError() {
        HttpResponse<String> page = sut.postForm("/users",
                Map.of("name", "Ada", "email", "ada@example.com", "age", "old", "country", "GB"));

        assertThat(page.statusCode()).isEqualTo(400);
        assertThat(page.body()).contains("data-error-code=\"age_invalid\"");
    }

    @Test
    void deleteRedirectsBack() {
        sut.postForm("/users", Map.of("name", "Ada", "email", "ada@example.com", "age", "36", "country", "GB"));

        HttpResponse<String> delete = sut.postForm("/users/1/delete", Map.of());

        assertThat(delete.statusCode()).isEqualTo(303);
        String location = delete.headers().firstValue("Location").orElseThrow();
        assertThat(sut.get(location).body()).doesNotContain("data-user-id=\"1\"").contains("User deleted");
    }
}
