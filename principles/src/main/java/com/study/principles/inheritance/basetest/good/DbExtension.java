package com.study.principles.inheritance.basetest.good;

import com.study.principles.inheritance.basetest.Db;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/** Resolves a {@link Db} connection per test, closed by JUnit. */
public final class DbExtension implements ParameterResolver {

    private static final Namespace NAMESPACE = Namespace.create(DbExtension.class);

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return parameter.getParameter().getType() == Db.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        return context.getStore(NAMESPACE).getOrComputeIfAbsent(Db.class, key -> Db.connect(), Db.class);
    }
}
