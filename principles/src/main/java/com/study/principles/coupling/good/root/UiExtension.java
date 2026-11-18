package com.study.principles.coupling.good.root;

import com.study.principles.coupling.Browser;
import com.study.principles.coupling.FakeBrowser;
import com.study.principles.coupling.good.Browsers;
import com.study.principles.coupling.good.Pages;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.util.Set;
import java.util.function.Supplier;

/**
 * Composition root: the only class that says {@code new FakeBrowser} (in real life: Playwright launch
 * options from config). It wires the object graph per test and owns its lifetime; pages and tests
 * know nothing about it — they only declare constructor / method parameters.
 * <pre>
 *   Browsers (per test, closed by JUnit)
 *     └─ main session: Browser ── Pages ── LoginPage / DashboardPage
 *     └─ extra sessions on demand: browsers.newSession()
 * </pre>
 * Scopes beyond "per test" (run-wide environment, refusing per-test objects in {@code @BeforeAll}) are
 * covered in {@code parallel.good.ShopExtension}; this one stays minimal on purpose.
 */
public final class UiExtension implements ParameterResolver {

    private static final Namespace NAMESPACE = Namespace.create(UiExtension.class);
    private static final Set<Class<?>> SUPPORTED = Set.of(Browsers.class, Pages.class, Browser.class);

    /** Swapping the browser kind (remote grid, WebKit, a recording decorator) is a change here only. */
    private final Supplier<Browser> launcher = FakeBrowser::new;

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return SUPPORTED.contains(parameter.getParameter().getType());
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        // AutoCloseable values in the Store are closed by JUnit when the context ends — also on failure
        Browsers browsers = store.getOrComputeIfAbsent(Browsers.class, key -> new Browsers(launcher), Browsers.class);
        Class<?> type = parameter.getParameter().getType();
        if (type == Browsers.class) return browsers;
        Browser main = store.getOrComputeIfAbsent(Browser.class, key -> browsers.launch(), Browser.class);
        if (type == Browser.class) return main;
        return store.getOrComputeIfAbsent(Pages.class, key -> new Pages(main), Pages.class);
    }
}
