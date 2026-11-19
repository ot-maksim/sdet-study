package com.study.template;

import com.study.framework.ui.UiSession;
import com.study.template.environment.EnvironmentProvider;
import com.study.template.scope.TestBeans;
import com.study.template.scope.TestScope;
import java.lang.reflect.Constructor;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * What the Spring context does not do for tests, next to Spring's own {@link SpringExtension}:
 * <ul>
 *   <li><b>parameters without {@code @Autowired}</b>: Spring injects a test method's parameter only if it is
 *       annotated; here any parameter whose type is a bean is resolved, so tests read as in stage 2;</li>
 *   <li><b>the test scope</b>: test-scoped beans are created inside {@link TestScope#within} for this test and
 *       destroyed after it ({@code afterEach}), with the steps' undo actions, also when the test failed;</li>
 *   <li><b>environment policy</b>: asks the {@link EnvironmentProvider} before any test; unavailable → skipped with
 *       the reason, or failed when {@code template.require-environment=true} (CI);</li>
 *   <li><b>a screenshot of a failed test</b>, only if the test had a browser.</li>
 * </ul>
 */
public final class TemplateExtension implements ExecutionCondition, ParameterResolver, AfterTestExecutionCallback, AfterEachCallback {

    private static final Namespace NAMESPACE = Namespace.create(TemplateExtension.class);

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (context.getTestClass().isEmpty()) {
            return ConditionEvaluationResult.enabled("no test class");
        }
        ApplicationContext spring = SpringExtension.getApplicationContext(context);
        Optional<String> reason = spring.getBean(EnvironmentProvider.class).unavailable();
        if (reason.isEmpty()) {
            return ConditionEvaluationResult.enabled("environment is available");
        }
        if (spring.getBean(TemplateProperties.class).requireEnvironment()) {
            throw new IllegalStateException(reason.get() + " (template.require-environment=true)");
        }
        return ConditionEvaluationResult.disabled(reason.get() + " (set template.require-environment=true to fail instead)");
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
            String name = (context.getRequiredTestClass().getSimpleName() + "-" + context.getDisplayName())
                    .replaceAll("[^A-Za-z0-9._-]+", "_");
            Path file = Path.of("target", "screenshots", name + ".png");
            try {
                session.screenshot(file);
                System.err.println("Screenshot of the failed test: " + file.toAbsolutePath());
            } catch (RuntimeException e) {
                // A broken screenshot must not replace the test's own failure in the report
                System.err.println("No screenshot for " + name + ": " + e.getMessage().lines().findFirst().orElse(""));
            }
        });
    }

    @Override
    public void afterEach(ExtensionContext context) {
        TestBeans beans = context.getStore(NAMESPACE).remove(TestBeans.class, TestBeans.class);
        if (beans != null) {
            beans.close();
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
