package com.study.review.bad.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.study.review.bad.api.UserApi;
import com.study.review.bad.db.DbUtils;
import com.study.sut.SutApp;
import com.study.sut.users.User;
import com.study.sut.users.ValidUser;
import io.restassured.response.Response;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/** Business steps over users. */
public class UserSteps {

    private final UserApi api = new UserApi();

    public void createUser(Map<String, Object> user) {
        Response response = api.createUser(user);
        ScenarioContext.put("request", user);
        ScenarioContext.put("response", response);
        ScenarioContext.put("userId", response.jsonPath().get("id"));
    }

    public void responseStatusShouldBe(int status) {
        assertThat(response().statusCode()).isEqualTo(status);
    }

    public void createdUserShouldHaveName(String name) {
        assertThat(response().jsonPath().getString("name")).isEqualTo(name);
    }

    public void userShouldMatchRequest() throws JsonProcessingException {
        User actual = SutApp.objectMapper().readValue(response().asString(), User.class);
        ValidUser expected = Expected.user(request());
        assertThat(actual.name()).isEqualTo(expected.name());
        assertThat(actual.email()).isEqualTo(expected.email());
        assertThat(actual.age()).isEqualTo(expected.age());
        assertThat(actual.country()).isEqualTo(expected.country());
    }

    public void userShouldExist() {
        long id = (Integer) ScenarioContext.get("userId");
        assertThat(api.getUser(id).statusCode()).isEqualTo(200);
    }

    public void userShouldBeInDb(Connection db) throws SQLException {
        Map<String, Object> row = DbUtils.findUserByEmail(db, (String) request().get("email"));
        assertThat(row).isNotEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> request() {
        return (Map<String, Object>) ScenarioContext.get("request");
    }

    private static Response response() {
        return (Response) ScenarioContext.get("response");
    }
}
