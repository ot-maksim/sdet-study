package com.study.project.report;

import io.qameta.allure.Allure;
import io.qameta.allure.Allure.StepContext;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

/**
 * The one place that turns "a call" into an Allure step; used by the decorators.
 * A step that throws is closed as failed ({@code AssertionError}) or broken (anything else) and the exception goes
 * on unchanged: reporting never changes what the test sees.
 *
 * <p>Outside a running test (context start-up, a probe) Allure has no test case to add the step to; the call
 * still runs, the step is only dropped (Allure logs it at WARN).
 */
public final class ReportSteps {

    @FunctionalInterface
    public interface Body<T> {
        T run(StepContext step) throws Throwable;
    }

    private ReportSteps() {
    }

    public static <T> T step(String name, Map<String, ?> parameters, Body<T> body) {
        return Allure.step(name, step -> {
            parameters.forEach((key, value) -> step.parameter(key, describe(value)));
            return body.run(step);
        });
    }

    public static void attachText(String name, String text) {
        Allure.addAttachment(name, "text/plain", text, ".txt");
    }

    /**
     * What a parameter looks like in the report: values as they print themselves; a lambda or an object without
     * its own {@code toString} (a page object) as its kind, not {@code UsersPage@1f2a}.
     */
    public static String describe(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Collection<?> collection && collection.size() > 5) {
            return collection.size() + " items";
        }
        Class<?> type = value.getClass();
        if (type.isSynthetic() || type.getSimpleName().contains("$$Lambda")) {
            return "(function)";
        }
        return ownToString(type) ? String.valueOf(value) : type.getSimpleName();
    }

    private static boolean ownToString(Class<?> type) {
        try {
            Method toString = type.getMethod("toString");
            return toString.getDeclaringClass() != Object.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
