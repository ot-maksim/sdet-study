package com.study.project.report;

import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.filter.Filter;

/**
 * Every HTTP exchange as a report step with the request and the response attached. A RestAssured filter handed to
 * the transport by the root: the api layer runs it without knowing what it is, the report tool stays in this
 * package. Outside a running test (a stand probe) Allure has no test to attach to and only logs it.
 */
public final class ApiReport {

    private ApiReport() {
    }

    public static Filter exchanges() {
        return new AllureRestAssured();
    }
}
