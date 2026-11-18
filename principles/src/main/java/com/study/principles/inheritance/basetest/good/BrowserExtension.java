package com.study.principles.inheritance.basetest.good;

import com.study.principles.inheritance.basetest.Browser;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.util.Optional;

/**
 * Resolves a {@link Browser}: launched on first request, one per test, closed by JUnit (the Store closes
 * {@link AutoCloseable} values) — also when the test fails. Logging in is the test's job: it is one line
 * and it says <i>who</i> the test is about.
 */
public final class BrowserExtension implements ParameterResolver {

    private static final Namespace NAMESPACE = Namespace.create(BrowserExtension.class);

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return parameter.getParameter().getType() == Browser.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        return context.getStore(NAMESPACE).getOrComputeIfAbsent(Browser.class, key -> Browser.launch(), Browser.class);
    }

    /** For cooperating extensions: the test's browser, if it asked for one. Never launches. */
    static Optional<Browser> started(ExtensionContext context) {
        return Optional.ofNullable(context.getStore(NAMESPACE).get(Browser.class, Browser.class));
    }
}
