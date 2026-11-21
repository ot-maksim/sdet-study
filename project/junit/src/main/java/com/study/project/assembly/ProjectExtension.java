package com.study.project.assembly;

import com.study.project.ui.UiSession;
import com.study.project.environment.EnvironmentProvider;
import com.study.project.report.Artifact;
import com.study.project.report.Artifacts;
import com.study.project.assembly.scope.TestBeans;
import com.study.project.assembly.scope.TestScope;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.support.AnnotationSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * What the Spring context does not do for tests, next to Spring's own {@link SpringExtension}:
 * <ul>
 *   <li><b>parameters without {@code @Autowired}</b>: Spring injects a test method's parameter only if it is
 *       annotated; here any parameter whose type is a bean is resolved, so a test lists what it needs;</li>
 *   <li><b>the test scope</b>: test-scoped beans are created inside {@link TestScope#within} for this test and
 *       destroyed after it ({@code afterEach}), with the steps' undo actions, also when the test failed;</li>
 *   <li><b>environment policy</b>: asks the {@link EnvironmentProvider} before any test; unavailable → skipped with
 *       the reason, or failed when {@code project.require-environment=true} (CI); for {@link EventTests} also
 *       whether the environment has a broker;</li>
 *   <li><b>parallelism within the browser pool</b>: more parallel tests than {@code project.ui.max-browsers}
 *       would wait for a browser and fail on its timeout, far from the cause; refused before the first test;</li>
 *   <li><b>a screenshot of a failed test</b>, only if the test had a browser — published to {@link Artifacts}:
 *       this class does not know whether it lands in a folder, in Allure or in both.</li>
 * </ul>
 */
public final class ProjectExtension implements ExecutionCondition, ParameterResolver, AfterTestExecutionCallback, AfterEachCallback {

    private static final Namespace NAMESPACE = Namespace.create(ProjectExtension.class);

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (context.getTestClass().isEmpty()) {
            return ConditionEvaluationResult.enabled("no test class");
        }
        ApplicationContext spring = SpringExtension.getApplicationContext(context);
        int maxBrowsers = spring.getBean(ProjectProperties.class).ui().maxBrowsers();
        Parallelism.of(context).filter(parallelism -> parallelism > maxBrowsers).ifPresent(parallelism -> {
            throw new IllegalStateException("JUnit runs up to " + parallelism + " tests at once, the browser pool has "
                    + maxBrowsers + " (project.ui.max-browsers): UI tests would wait and time out. Lower "
                    + "junit.jupiter.execution.parallel.config.fixed.parallelism or raise project.ui.max-browsers");
        });
        EnvironmentProvider provider = spring.getBean(EnvironmentProvider.class);
        Optional<String> reason = provider.unavailable();
        if (reason.isEmpty() && AnnotationSupport.isAnnotated(context.getTestClass(), EventTests.class)) {
            reason = provider.eventsUnavailable();
        }
        if (reason.isEmpty()) {
            return ConditionEvaluationResult.enabled("environment is available");
        }
        if (spring.getBean(ProjectProperties.class).requireEnvironment()) {
            throw new IllegalStateException(reason.get() + " (project.require-environment=true)");
        }
        return ConditionEvaluationResult.disabled(reason.get() + " (set project.require-environment=true to fail instead)");
    }

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        if (parameter.getDeclaringExecutable() instanceof Constructor
                || parameter.isAnnotated(Autowired.class) || parameter.isAnnotated(Qualifier.class) || parameter.isAnnotated(Value.class)) {
            return false;   // Spring's own cases: SpringExtension resolves them
        }
        Class<?> type = parameter.getParameter().getType();
        return !ApplicationContext.class.isAssignableFrom(type)
                && SpringExtension.getApplicationContext(context).getBeanNamesForType(type).length > 0;
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        Class<?> type = parameter.getParameter().getType();
        ApplicationContext spring = SpringExtension.getApplicationContext(context);
        try {
            if (context.getTestMethod().isEmpty()) {
                return spring.getBean(type);   // @BeforeAll: a test-scoped bean fails here with TestScope's message
            }
            TestBeans beans = context.getStore(NAMESPACE).getOrComputeIfAbsent(TestBeans.class,
                    key -> new TestBeans(context.getRequiredTestClass().getSimpleName() + "." + context.getDisplayName()),
                    TestBeans.class);
            return TestScope.within(beans, () -> spring.getBean(type));
        } catch (RuntimeException e) {
            throw new ParameterResolutionException("Cannot resolve " + type.getSimpleName() + " for "
                    + parameter.getDeclaringExecutable().getName() + ": " + rootMessage(e), e);
        }
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        if (context.getExecutionException().isEmpty()) {
            return;
        }
        TestBeans beans = context.getStore(NAMESPACE).get(TestBeans.class, TestBeans.class);
        if (beans == null) {
            return;
        }
        beans.peek(UiSession.class).ifPresent(session -> {
            String test = context.getRequiredTestClass().getSimpleName() + "-" + context.getDisplayName();
            try {
                byte[] png = screenshot(session);
                SpringExtension.getApplicationContext(context).getBean(Artifacts.class)
                        .publish(new Artifact(test, "screenshot", "image/png", "png", png));
            } catch (RuntimeException e) {
                // A broken screenshot must not replace the test's own failure in the report
                System.err.println("No screenshot for " + test + ": " + e.getMessage().lines().findFirst().orElse(""));
            }
        });
    }

    /** {@code UiSession} writes to a file only; the bytes go to every listener. */
    private static byte[] screenshot(UiSession session) {
        try {
            Path file = Files.createTempFile("screenshot-", ".png");
            try {
                session.screenshot(file);
                return Files.readAllBytes(file);
            } finally {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        TestBeans beans = context.getStore(NAMESPACE).remove(TestBeans.class, TestBeans.class);
        if (beans != null) {
            beans.close();
        }
    }

    /** How many tests JUnit may run at once, from the same keys it reads (junit-platform.properties, -D). */
    static final class Parallelism {

        private static final String PREFIX = "junit.jupiter.execution.parallel.";

        static Optional<Integer> of(ExtensionContext context) {
            if (!context.getConfigurationParameter(PREFIX + "enabled").map(Boolean::parseBoolean).orElse(false)) {
                return Optional.of(1);
            }
            String strategy = context.getConfigurationParameter(PREFIX + "config.strategy").orElse("dynamic");
            return switch (strategy) {
                case "fixed" -> context.getConfigurationParameter(PREFIX + "config.fixed.parallelism").map(Integer::parseInt);
                case "dynamic" -> Optional.of((int) Math.ceil(Runtime.getRuntime().availableProcessors()
                        * context.getConfigurationParameter(PREFIX + "config.dynamic.factor").map(Double::parseDouble).orElse(1.0)));
                default -> Optional.empty();   // custom: its own business
            };
        }
    }

    /** Spring wraps the reason in several BeanCreationExceptions; the innermost message is the one that says what to do. */
    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
