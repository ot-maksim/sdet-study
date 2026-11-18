package com.study.principles.inheritance.basetest.good;

import com.study.principles.inheritance.basetest.ApiClient;
import com.study.principles.inheritance.basetest.User;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Resolves {@link ApiClient} and a fresh {@link User} (created through the API, named after the test).
 * Nothing is created unless a parameter asks for it.
 */
public final class ApiExtension implements ParameterResolver {

    private static final Namespace NAMESPACE = Namespace.create(ApiExtension.class);

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        Class<?> type = parameter.getParameter().getType();
        return type == ApiClient.class || type == User.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        if (parameter.getParameter().getType() == ApiClient.class) return client(context);
        return context.getStore(NAMESPACE).getOrComputeIfAbsent(User.class,
                key -> client(context).createUser(context.getRequiredTestMethod().getName()), User.class);
    }

    private static ApiClient client(ExtensionContext context) {
        return context.getStore(NAMESPACE).getOrComputeIfAbsent(ApiClient.class, key -> new ApiClient(), ApiClient.class);
    }
}
