package com.study.project.assembly;

import com.study.project.api.ApiSettings;
import com.study.project.api.ApiTransport;
import com.study.project.api.ContractFilter;
import com.study.project.api.users.NewUsers;
import com.study.project.api.users.UserApi;
import com.study.project.assembly.scope.TestScope;
import com.study.project.assembly.scope.TestScopeConfigurer;
import com.study.project.assembly.scope.TestScoped;
import com.study.project.core.Secret;
import com.study.project.core.Unique;
import com.study.project.core.lifecycle.Cleanup;
import com.study.project.db.Database;
import com.study.project.db.DbSettings;
import com.study.project.db.users.UserRows;
import com.study.project.domain.ApiUserPrecondition;
import com.study.project.domain.DbUserPrecondition;
import com.study.project.domain.UserChecks;
import com.study.project.domain.UserPrecondition;
import com.study.project.domain.UserSteps;
import com.study.project.environment.ContainersEnvironment;
import com.study.project.environment.EnvironmentProvider;
import com.study.project.environment.StandEnvironment;
import com.study.project.environment.SutEnvironment;
import com.study.project.events.EventClient;
import com.study.project.events.users.UserEvents;
import com.study.project.report.AllureArtifacts;
import com.study.project.report.ApiReport;
import com.study.project.report.ArtifactFiles;
import com.study.project.report.ArtifactListener;
import com.study.project.report.Artifacts;
import com.study.project.report.ReportingUserPrecondition;
import com.study.project.report.ReportingUsersScreen;
import com.study.project.ui.BrowserPool;
import com.study.project.ui.BrowserProfiles;
import com.study.project.ui.UiSession;
import com.study.project.ui.UiSettings;
import com.study.project.ui.users.UsersPage;
import com.study.project.ui.users.UsersScreen;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;

/**
 * The composition root: one {@code @Bean} method per object of the graph. Deliberately <b>no component scanning
 * and no auto-configuration</b>: every {@code new} of the project is in this one class, the layers stay free of
 * Spring annotations (ArchitectureTest), and the graph is read here, not reconstructed from annotations spread over
 * packages.
 *
 * <pre>
 * run  (singleton, lazy: spring.main.lazy-initialization)
 *   EnvironmentProvider (stand | containers) ── SutEnvironment ── ApiTransport ── UserApi    Unique ── NewUsers
 *                                                    │             (contract check, report)
 *                                                    ├── Database ── UserRows ── UserChecks
 *                                                    ├── UiSettings ── BrowserPool            Clock
 *                                                    └── EventClient (one consumer thread per run)
 *   Artifacts ── ArtifactFiles, AllureArtifacts (observers of what a failed test leaves: screenshots)
 * test (@TestScoped: one per test method, destroyed after it)
 *   Cleanup ── UserPrecondition (api | db, decorated: a report step) ── UserSteps
 *           ── UiSession (destroy = close) ── UsersScreen (decorated: a report step per action)
 *   UserEvents (subscribed before the test body, unsubscribed after it)
 * </pre>
 *
 * The report reaches the layers only through their seams: a filter handed to the transport, decorators of the
 * interfaces chosen here. {@code UserSteps} has no step of its own: it is made of API calls, precondition and screen
 * actions, and each of those is one.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProjectProperties.class)
public class ProjectConfiguration {

    /** The contract the client was generated from, copied next to the classes by the build. */
    static final String CONTRACT = "contract/openapi.yaml";

    /** How long a subscription may take (assign, find the end of every partition) before the test fails. */
    private static final Duration SUBSCRIBE_TIMEOUT = Duration.ofSeconds(30);

    /** Static: a bean factory post-processor must exist before this class's own beans. */
    @Bean
    static TestScopeConfigurer testScope() {
        return new TestScopeConfigurer();
    }

    /** {@code project.stand.db-password} binds straight into a {@link Secret}; a lambda would hide the generic types from Spring. */
    @Bean
    @ConfigurationPropertiesBinding
    static Converter<String, Secret> secretConverter() {
        return new SecretConverter();
    }

    // ---- where the SUT is ----------------------------------------------------------------------------------

    /** The one choice of environment. Destroy method {@code close} is inferred: containers stop with the context. */
    @Bean
    EnvironmentProvider environmentProvider(ProjectProperties properties) {
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

    /** The contract check outermost: it sees the response after the report attached it, and fails the call. */
    @Bean
    ApiTransport apiTransport(SutEnvironment environment, ProjectProperties properties) {
        return new ApiTransport(new ApiSettings(environment.sutUrl(), properties.api().timeout()),
                List.of(ContractFilter.fromClasspath(CONTRACT), ApiReport.exchanges()));
    }

    @Bean
    UserApi userApi(ApiTransport transport) {
        return new UserApi(transport);
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
    UiSettings uiSettings(SutEnvironment environment, ProjectProperties properties) {
        ProjectProperties.Ui ui = properties.ui();
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

    // ---- report: observers of the artifacts a failed test leaves (the runner glue publishes) ----------------

    @Bean
    ArtifactFiles artifactFiles() {
        return new ArtifactFiles(Path.of("target", "artifacts"));
    }

    @Bean
    AllureArtifacts allureArtifacts() {
        return new AllureArtifacts();
    }

    /** Every {@link ArtifactListener} bean is subscribed: a new destination is one more {@code @Bean}. */
    @Bean
    Artifacts artifacts(List<ArtifactListener> listeners) {
        return new Artifacts(listeners);
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
    UserPrecondition userPrecondition(ProjectProperties properties, UserApi api, UserRows rows, Clock clock, Cleanup cleanup) {
        ProjectProperties.Domain.Channel channel = properties.domain().userPrecondition();
        UserPrecondition precondition = switch (channel) {
            case API -> new ApiUserPrecondition(api, cleanup);
            case DB -> new DbUserPrecondition(rows, clock, cleanup);
        };
        return new ReportingUserPrecondition(precondition, channel.name().toLowerCase());
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
    UserEvents userEvents(EventClient client, ProjectProperties properties) {
        return new UserEvents(client, properties.events().timeout());
    }

    /** A native implementation (Appium) would be chosen here, like the precondition channel. */
    @Bean
    @TestScoped
    UsersScreen usersScreen(UiSession session) {
        return new ReportingUsersScreen(new UsersPage(session.page()));
    }

    static final class SecretConverter implements Converter<String, Secret> {

        @Override
        public Secret convert(String value) {
            return Secret.of(value);
        }
    }
}
