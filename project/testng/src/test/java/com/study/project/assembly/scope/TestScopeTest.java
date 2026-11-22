package com.study.project.assembly.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.NestedExceptionUtils;

/**
 * The test scope on a tiny context: one instance per test, destroyed with the test's undo actions, refused elsewhere.
 * {@code singleThreaded}: the context is a field of the one instance TestNG makes, rebuilt before every method.
 */
@Test(singleThreaded = true)
public class TestScopeTest {

    static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());

    static final class Connection {
    }

    static final class Session implements AutoCloseable {
        @Override
        public void close() {
            LOG.add("close session");
        }
    }

    /** A run-wide helper that keeps a test's session: the captive dependency. */
    record SessionHolder(Session session) {
    }

    @Configuration(proxyBeanMethods = false)
    static class Wiring {
        @Bean
        static TestScopeConfigurer testScope() {
            return new TestScopeConfigurer();
        }

        @Bean
        Connection connection() {
            return new Connection();
        }

        @Bean
        @TestScoped
        Session session(Connection connection) {
            return new Session();
        }

        @Bean
        @Lazy   // created on first use, i.e. inside some test: exactly when a captive dependency happens
        SessionHolder sessionHolder(Session session) {
            return new SessionHolder(session);
        }
    }

    private AnnotationConfigApplicationContext spring;

    @BeforeMethod
    public void start() {
        spring = new AnnotationConfigApplicationContext(Wiring.class);
    }

    @AfterMethod(alwaysRun = true)
    public void close() {
        spring.close();
        LOG.clear();
    }

    @Test
    public void oneInstancePerTestAndOneRunWideForAll() {
        TestBeans first = new TestBeans("first");
        TestBeans second = new TestBeans("second");

        Session a = TestScope.within(first, () -> spring.getBean(Session.class));
        Session again = TestScope.within(first, () -> spring.getBean(Session.class));
        Session b = TestScope.within(second, () -> spring.getBean(Session.class));

        assertThat(again).isSameAs(a);
        assertThat(b).isNotSameAs(a);
        assertThat(TestScope.within(second, () -> spring.getBean(Connection.class))).isSameAs(spring.getBean(Connection.class));
    }

    @Test
    public void beanDestructionAndTheStepsUndoRunInOneReverseOrder() {
        TestBeans test = new TestBeans("test");
        TestScope.within(test, () -> spring.getBean(Session.class));            // the browser first
        test.cleanup().defer("delete user", () -> LOG.add("delete user"));     // then a user created through it

        test.close();

        assertThat(LOG).containsExactly("delete user", "close session");
    }

    @Test
    public void outsideATestATestBeanIsRefusedWithTheReason() {
        assertThatThrownBy(() -> spring.getBean(Session.class))
                .satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e))
                        .hasMessageContaining("'session' lives per test")
                        .hasMessageContaining("@BeforeAll"));
    }

    @Test
    public void anotherThreadDoesNotInheritTheTest() {
        TestBeans test = new TestBeans("test");

        Throwable fromOtherThread = TestScope.within(test, () ->
                CompletableFuture.runAsync(() -> spring.getBean(Session.class)).handle((ok, e) -> e).join());

        assertThat(NestedExceptionUtils.getMostSpecificCause(fromOtherThread)).hasMessageContaining("outside a test method");
    }

    @Test
    public void aSingletonHoldingATestBeanIsRefused() {
        TestBeans test = new TestBeans("test");

        assertThatThrownBy(() -> TestScope.within(test, () -> spring.getBean(SessionHolder.class)))
                .hasMessageContaining("Singleton 'sessionHolder' depends on test-scoped 'session'")
                .hasMessageContaining("captive dependency");
    }
}
