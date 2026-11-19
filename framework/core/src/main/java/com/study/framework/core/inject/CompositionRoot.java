package com.study.framework.core.inject;

import com.study.framework.core.config.Config;
import com.study.framework.core.data.Unique;
import com.study.framework.core.lifecycle.Cleanup;
import java.lang.System.Logger.Level;
import java.util.Optional;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Mechanics of a hand-written composition root as a JUnit 5 extension. The root itself — the one class that
 * knows every module — lives at the top of the dependency graph (the {@code tests} module) and only says what
 * is built how:
 *
 * <pre>
 * public final class SutComposition extends CompositionRoot {
 *     protected void bind(Bindings b) {
 *         b.run(ApiSettings.class, c -&gt; c.get(Config.class).bind(ApiSettings::from));
 *         b.run(UserApi.class, c -&gt; new UserApi(c.get(ApiSettings.class)));
 *         b.test(UsersPage.class, c -&gt; new UsersPage(c.get(Page.class)));
 *     }
 * }
 * </pre>
 *
 * Tests declare parameters ({@code void creates(UserApi api, Cleanup cleanup)}); nothing is static, there is no
 * base test class. Built in: {@link Config} and {@link Unique} (RUN), {@link Cleanup} (TEST, the test's scope).
 *
 * <p>Guarded mistakes, each with a message that names the path:
 * a TEST object asked for in {@code @BeforeAll} or a {@code PER_CLASS} constructor (one object for all parallel
 * tests of the class); a RUN object depending on a TEST one (captive dependency); a dependency cycle.
 *
 * <p>Why an abstract class with one hook and not composition: JUnit instantiates {@code @ExtendWith} classes
 * by their no-arg constructor, so the wiring has to come with the class. The subclass adds no state.
 */
public abstract class CompositionRoot implements ParameterResolver {

    private static final System.Logger LOG = System.getLogger(CompositionRoot.class.getName());
    private static final Namespace NAMESPACE = Namespace.create(CompositionRoot.class);

    // Lazy, not built in the constructor: calling an overridable method from a constructor would run
    // the subclass's bind() before the subclass's own fields are initialized (see basics/init)
    private volatile Bindings bindings;

    /** Declares what is built how; called once per extension instance. */
    protected abstract void bind(Bindings bindings);

    @Override
    public final boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return bindings().contains(parameter.getParameter().getType());
    }

    @Override
    public final Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        Class<?> type = parameter.getParameter().getType();
        Bindings all = bindings();
        // One run scope per root class: two roots in one run do not share objects
        Instances run = context.getRoot().getStore(NAMESPACE).getOrComputeIfAbsent(getClass().getName() + ":run",
                key -> new Instances(Lifetime.RUN, all, null, "run"), Instances.class);
        try {
            if (all.get(type).lifetime() == Lifetime.RUN) {
                return run.get(type);
            }
            if (context.getTestMethod().isEmpty()) {
                throw new ParameterResolutionException(type.getSimpleName() + " lives per test, but "
                        + parameter.getDeclaringExecutable().getName() + " runs once for " + context.getDisplayName()
                        + ": all its tests would share one. Ask for it in the test or in @BeforeEach.");
            }
            Instances test = context.getStore(NAMESPACE).getOrComputeIfAbsent(getClass().getName() + ":test",
                    key -> new Instances(Lifetime.TEST, all, run, context.getDisplayName()), Instances.class);
            return test.get(type);
        } catch (ParameterResolutionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ParameterResolutionException("Cannot resolve " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * The test's object of {@code type} if the test already asked for it, without creating it: a screenshot
     * after a failure needs the test's browser page, but must not launch a browser for an API test.
     */
    protected final <T> Optional<T> createdInTest(ExtensionContext context, Class<T> type) {
        Instances test = context.getStore(NAMESPACE).get(getClass().getName() + ":test", Instances.class);
        return test == null ? Optional.empty() : test.peek(type);
    }

    private Bindings bindings() {
        Bindings result = bindings;
        if (result == null) {
            synchronized (this) {
                result = bindings;
                if (result == null) {
                    result = new Bindings();
                    bind(result);
                    if (!result.contains(Config.class)) {
                        result.run(Config.class, c -> {
                            Config config = Config.load();
                            LOG.log(Level.INFO, config.describe());
                            return config;
                        });
                    }
                    if (!result.contains(Unique.class)) {
                        result.run(Unique.class, c -> new Unique());
                    }
                    if (!result.contains(Cleanup.class)) {
                        result.test(Cleanup.class, Components::cleanup);
                    }
                    bindings = result;
                }
            }
        }
        return result;
    }
}
