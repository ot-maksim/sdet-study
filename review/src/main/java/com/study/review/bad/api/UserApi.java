package com.study.review.bad.api;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Map;

/** REST client for {@code /api/users}. */
public class UserApi {

    public Response createUser(Map<String, Object> user) {
        return given()
                .contentType(ContentType.JSON)
                .body(user)
                .when()
                .post("/api/users")
                .then()
                .extract()
                .response();
    }

    public Response getUser(long id) {
        return given().when().get("/api/users/{id}", id).then().extract().response();
    }

    public Response getAllUsers() {
        return given().when().get("/api/users").then().extract().response();
    }

    public Response deleteUser(long id) {
        return given().when().delete("/api/users/{id}", id).then().extract().response();
    }
}
