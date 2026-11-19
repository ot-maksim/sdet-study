package com.study.review.bad.core;

import com.study.review.bad.api.UserApi;
import com.study.review.bad.config.FrameworkConfig;
import com.study.review.bad.data.TestData;
import com.study.review.bad.ui.UsersPage;
import io.restassured.RestAssured;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;

/** Base class for all tests: configures API, browser and database, cleans test data after each test. */
@ExtendWith(ScreenshotOnFailure.class)
public abstract class BaseTest {

    protected static FrameworkConfig config;
    protected static Connection db;
    protected static UserApi userApi;
    protected static UsersPage usersPage;

    @BeforeAll
    static void setUpFramework() throws SQLException {
        config = FrameworkConfig.getInstance();
        RestAssured.baseURI = config.get("base.url");
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        Driver.start();
        db = DriverManager.getConnection(config.get("db.url"), config.get("db.user"), config.get("db.password"));
        userApi = new UserApi();
        usersPage = new UsersPage();
    }

    @AfterEach
    void cleanUpTestData() {
        TestData.cleanUp();
    }

    @AfterAll
    static void tearDownFramework() throws SQLException {
        Driver.stop();
        db.close();
    }
}
