package com.study.template;

import com.study.framework.api.ApiClient;
import com.study.framework.api.ApiSettings;
import com.study.framework.api.users.NewUsers;
import com.study.framework.api.users.UserApi;
import com.study.framework.core.config.Secret;
import com.study.framework.core.data.Unique;
import com.study.framework.core.lifecycle.Cleanup;
import com.study.framework.db.Database;
import com.study.framework.db.DbSettings;
import com.study.framework.db.users.UserRows;
import com.study.framework.domain.ApiUserPrecondition;
import com.study.framework.domain.DbUserPrecondition;
import com.study.framework.domain.UserChecks;
import com.study.framework.domain.UserPrecondition;
import com.study.framework.domain.UserSteps;
import com.study.framework.ui.BrowserPool;
import com.study.framework.ui.BrowserProfiles;
import com.study.framework.ui.UiSession;
import com.study.framework.ui.UiSettings;
import com.study.framework.ui.users.UsersPage;
import com.study.framework.ui.users.UsersScreen;
import com.study.template.environment.ContainersEnvironment;
import com.study.template.environment.EnvironmentProvider;
import com.study.template.environment.StandEnvironment;
import com.study.template.environment.SutEnvironment;
import com.study.template.events.EventClient;
import com.study.template.events.users.UserEvents;
import com.study.template.scope.TestScope;
import com.study.template.scope.TestScopeConfigurer;
import com.study.template.scope.TestScoped;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;

/**
 * The composition root, Spring edition: the same graph as stage 2's {@code SutComposition}, one {@code @Bean}
 * method per binding. Deliberately <b>no component scanning and no auto-configuration</b>: every {@code new} is
 * still in this one class, the framework's layers stay free of Spring annotations, and the graph is read here,
 * not reconstructed from annotations spread over modules.
 *
 * <pre>
 * run  (singleton, lazy: spring.main.lazy-initialization)
 *   EnvironmentProvider (stand | containers) ── SutEnvironment ── ApiClient ── UserApi     Unique ── NewUsers
 *                                                              ── Database ── UserRows ── UserChecks
 *                                                              ── UiSettings ── BrowserPool        Clock
 *                                                              ── EventClient (one consumer thread per run)
 * test (@TestScoped: one per test method, destroyed after it)
 *   Cleanup ── UserPrecondition (api | db) ── UserSteps
 *           ── UiSession (destroy = close) ── UsersScreen
 *   UserEvents (subscribed before the test body, unsubscribed after it)
 * </pre>
 *
 * Stage 2 → here: {@code b.run(...)} → {@code @Bean}; {@code b.test(...)} → {@code @Bean @TestScoped};
 * {@code c.get(X.class)} → a parameter of the method; {@code c.cleanup().own(x)} → the bean's destroy method;
 * {@code Config} + {@code env/*.properties} → {@link TemplateProperties} + {@code application-<profile>.yml}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TemplateProperties.class)
public class TemplateConfiguration {

    /** How long a subscription may take (assign, find the end of every partition) before the test fails. */
    private static final Duration SUBSCRIBE_TIMEOUT = Duration.ofSeconds(30);

    /** Static: a bean factory post-processor must exist before this class's own beans. */
    @Bean
    static TestScopeConfigurer testScope() {
        return new TestScopeConfigurer();
    }

    /** {@code template.stand.db-password} binds straight into a {@link Secret}; a lambda would hide the generic types from Spring. */
    @Bean
    @ConfigurationPropertiesBinding
    static Converter<String, Secret> secretConverter() {
        return new SecretConverter();
    }

    // ---- where the SUT is ----------------------------------------------------------------------------------

    /** The one choice of environment. Destroy method {@code close} is inferred: containers stop with the context. */
    @Bean
    EnvironmentProvider environmentProvider(TemplateProperties properties) {
        return switch (properties.environment()) {
            case STAND -> new StandEnvironment(properties.stand(), properties.api().timeout());
            case CONTAINERS -> new ContainersEnvironment(properties.containers());
        };
    }

    @Bean
    SutEnvironment sutEnvironment(EnvironmentProvider provider) {
        return provider.start();
    }

    // ---- run: shared by parallel tests, immutable or thread-safe -----------------------------------------------

    @Bean
    ApiClient apiClient(SutEnvironment environment, TemplateProperties properties) {
        return new ApiClient(new ApiSettings(environment.sutUrl(), properties.api().timeout()));
    }

    @Bean
    UserApi userApi(ApiClient client) {
        return new UserApi(client);
    }

    @Bean
    Unique unique() {
        return new Unique();
    }

    @Bean
    NewUsers newUsers(Unique unique) {
        return new NewUsers(unique);
    }

    @Bean
    Database database(SutEnvironment environment) {
        return new Database(new DbSettings(environment.dbUrl(), environment.dbUser(), environment.dbPassword()));
    }

    @Bean
    UserRows userRows(Database database) {
        return new UserRows(database);
    }

    @Bean
    UserChecks userChecks(UserRows rows) {
        return new UserChecks(rows);
    }

    @Bean
    UiSettings uiSettings(SutEnvironment environment, TemplateProperties properties) {
        TemplateProperties.Ui ui = properties.ui();
        return new UiSettings(environment.sutUrl(), ui.profile(), ui.headless(), ui.timeout(), ui.maxBrowsers());
    }

    /** {@code close} inferred: the browsers are closed when the context closes, at the end of the run. */
    @Bean
    BrowserPool browserPool(UiSettings settings) {
        return new BrowserPool(settings);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * One Kafka consumer (and its poll thread) for the run, shared by parallel tests; {@code close} inferred.
     * Only {@code @EventTests} get here: for the others the environment's broker is not checked and may be missing.
     */
    @Bean
    EventClient eventClient(SutEnvironment environment) {
        String bootstrap = environment.kafkaBootstrap().orElseThrow(() -> new IllegalStateException(
                "The environment has no Kafka (" + environment + "): a test that reads events must be @EventTests"));
        return new EventClient(bootstrap, Map.of(), SUBSCRIBE_TIMEOUT);
    }

    // ---- test: one per test method -----------------------------------------------------------------------------

    /**
     * {@code destroyMethod = ""}: Spring would infer {@code close()} from the object behind the interface and
     * register the cleanup to be closed by itself; it is closed by the test's end instead.
     */
    @Bean(destroyMethod = "")
    @TestScoped
    Cleanup cleanup() {
        return TestScope.currentCleanup();
    }

    @Bean
    @TestScoped
    UserPrecondition userPrecondition(TemplateProperties properties, UserApi api, UserRows rows, Clock clock, Cleanup cleanup) {
        return switch (properties.domain().userPrecondition()) {
            case API -> new ApiUserPrecondition(api, cleanup);
            case DB -> new DbUserPrecondition(rows, clock, cleanup);
        };
    }

    @Bean
    @TestScoped
    UserSteps userSteps(UserPrecondition precondition, NewUsers newUsers, UserApi api, Cleanup cleanup) {
        return new UserSteps(precondition, newUsers, api, cleanup);
    }

    /** {@code close} inferred and registered with the test scope: the browser goes back to the pool after the test. */
    @Bean
    @TestScoped
    UiSession uiSession(BrowserPool pool, UiSettings settings) {
        return new UiSession(pool, BrowserProfiles.get(settings.profile()), settings);
    }

    /** {@code close} inferred and registered with the test scope: the subscription ends with the test. */
    @Bean
    @TestScoped
    UserEvents userEvents(EventClient client, TemplateProperties properties) {
        return new UserEvents(client, properties.events().timeout());
    }

    /** A native implementation (stage 4d, Appium) would be chosen here, like the precondition channel. */
    @Bean
    @TestScoped
    UsersScreen usersScreen(UiSession session) {
        return new UsersPage(session.page());
    }

    static final class SecretConverter implements Converter<String, Secret> {

        @Override
        public Secret convert(String value) {
            return Secret.of(value);
        }
    }
}
