package com.study.principles.parallel.good;

import com.study.principles.parallel.FakeWebDriver;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.util.Set;

/**
 * Composition root: decides the <b>scope</b> of every object a test can ask for. Tests just declare
 * parameters; no static fields, no base class.
 *
 * <table>
 *   <tr><th>Parameter</th><th>Scope</th><th>Where it lives</th></tr>
 *   <tr><td>{@link ShopEnvironment}</td><td>run</td><td>root Store: created once, closed after the last test</td></tr>
 *   <tr><td>{@link ShopApi}</td><td>run</td><td>immutable, so the scope does not matter</td></tr>
 *   <tr><td>{@link FakeWebDriver}</td><td>test method</td><td>the test's Store: new per test, quit after it</td></tr>
 *   <tr><td>{@link TestUsers}</td><td>test method</td><td>the test's Store: leases returned after it</td></tr>
 *   <tr><td>{@link ProfilePage}</td><td>test method</td><td>built over the test's driver: the same session</td></tr>
 * </table>
 *
 * <h2>The unit of parallelism is the test method, never the class</h2>
 * JUnit runs methods of one class concurrently ({@code @Execution(CONCURRENT)}), so a per-test object
 * must not land in the <i>class</i> Store: asked for in {@code @BeforeAll}, in the constructor of a
 * {@code @TestInstance(PER_CLASS)} class, or in {@code @AfterAll}, it would be one object for all the
 * class's parallel methods, which is the static-driver bug again. That is refused with a clear message.
 * {@code @BeforeEach}/{@code @AfterEach} run in the test method's context and get the test's instance.
 *
 * <p>{@code getOrComputeIfAbsent} is thread-safe and computes the value at most once, so parallel tests
 * that start at the same moment still get one environment. Stored {@link AutoCloseable}s are closed by
 * JUnit when their scope ends, also when the test fails.
 *
 * <p>Why not a {@code ThreadLocal}: in JUnit a test's thread is an implementation detail of the
 * scheduler, a test is not. See {@code threadlocal.DriverManager}.
 */
public final class ShopExtension implements ParameterResolver {

    private static final Namespace NAMESPACE = Namespace.create(ShopExtension.class);
    private static final Set<Class<?>> RUN_SCOPED = Set.of(ShopEnvironment.class, ShopApi.class);
    private static final Set<Class<?>> TEST_SCOPED = Set.of(FakeWebDriver.class, TestUsers.class, ProfilePage.class);

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        Class<?> type = parameter.getParameter().getType();
        return RUN_SCOPED.contains(type) || TEST_SCOPED.contains(type);
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        Class<?> type = parameter.getParameter().getType();
        ShopEnvironment environment = context.getRoot().getStore(NAMESPACE)
                .getOrComputeIfAbsent(ShopEnvironment.class, key -> ShopEnvironment.connect(ShopConfig.DEFAULT),
                        ShopEnvironment.class);
        if (type == ShopEnvironment.class) {
            return environment;
        }
        if (type == ShopApi.class) {
            return environment.api();
        }
        if (context.getTestMethod().isEmpty()) {
            throw new ParameterResolutionException(type.getSimpleName() + " is per test method, but "
                    + parameter.getDeclaringExecutable().getName() + " runs once for the whole class ("
                    + context.getDisplayName() + "): all its parallel tests would share one. "
                    + "Ask for it in the test or in @BeforeEach.");
        }
        ExtensionContext.Store perTest = context.getStore(NAMESPACE);
        if (type == TestUsers.class) {
            return perTest.getOrComputeIfAbsent(TestUsers.class, key -> environment.newTestUsers(), TestUsers.class);
        }
        FakeWebDriver driver = perTest.getOrComputeIfAbsent(FakeWebDriver.class, key -> environment.newDriver(),
                FakeWebDriver.class);
        return type == ProfilePage.class ? new ProfilePage(driver) : driver;
    }
}
