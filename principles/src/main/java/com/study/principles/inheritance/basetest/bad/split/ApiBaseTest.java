package com.study.principles.inheritance.basetest.bad.split;

import com.study.principles.inheritance.basetest.ApiClient;
import org.junit.jupiter.api.BeforeEach;

/**
 * The usual "fix" for the god class: split it by channel into a hierarchy. Works until a test needs two
 * branches at once — see {@link UiDbBaseTest}.
 * <pre>
 *   ApiBaseTest
 *   ├── UiBaseTest
 *   │   └── UiDbBaseTest   (copies DbBaseTest: Java has one superclass)
 *   └── DbBaseTest
 * </pre>
 * Add "mobile" or "Kafka" and the tree doubles: UiMobileDbBaseTest, ApiKafkaDbBaseTest, ...
 */
public abstract class ApiBaseTest {

    protected ApiClient api;

    @BeforeEach
    void apiSetUp() {
        api = new ApiClient();
    }
}
