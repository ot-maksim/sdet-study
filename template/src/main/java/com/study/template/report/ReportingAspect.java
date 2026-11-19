package com.study.template.report;

import com.study.framework.api.ApiResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.CodeSignature;

/**
 * Steps and API exchanges in the report for classes that have <b>no seam to decorate</b>: {@code UserSteps} and
 * {@code ApiClient} are final classes of stage 2 with no interface, and they must not learn about Allure.
 * Woven when the classes load ({@code -javaagent:aspectjweaver}, see {@code template/pom.xml} and
 * {@code META-INF/aop.xml}); without the agent the run is the same, only without these steps —
 * {@code ReportingTest.theAspectIsWoven} turns that silence into a failure.
 *
 * <p>The price of not touching the classes: the pointcuts name methods by string. Rename {@code ApiClient.send}
 * and the API attachments are gone without a compile error; {@code ReportingTest} checks that every pointcut
 * still produces its step.
 * Where an interface exists, a decorator does the same explicitly ({@link ReportingUserPrecondition},
 * {@link ReportingUsersScreen}).
 */
@Aspect
public class ReportingAspect {

    /** Every business step of the domain facade: {@code UserSteps.registerThroughApi(user=...)}. */
    @Around("execution(public * com.study.framework.domain.UserSteps.*(..))")
    public Object userStep(ProceedingJoinPoint call) throws Throwable {
        return asStep(call);
    }

    /**
     * Every HTTP exchange, whichever resource client made it: a private method — the one place that has the
     * request and the response together. {@code send(Method, path, query, body)}.
     */
    @Around("execution(private com.study.framework.api.ApiResponse com.study.framework.api.ApiClient.send(..))")
    public Object apiExchange(ProceedingJoinPoint call) throws Throwable {
        Object[] args = call.getArgs();
        Map<?, ?> query = (Map<?, ?>) args[2];
        String request = args[0] + " " + args[1] + (query.isEmpty() ? "" : "?" + query);
        return ReportSteps.step(request, Map.of(), step -> {
            if (args[3] != null) {
                ReportSteps.attachText("request body", String.valueOf(args[3]));
            }
            ApiResponse response = (ApiResponse) call.proceed();
            step.name(request + " -> " + response.status());
            ReportSteps.attachText("response", response.toString());
            return response;
        });
    }

    /** What the test waited for on the topic and what came (or that nothing did). */
    @Around("execution(public * com.study.template.events.users.UserEvents.created(..))")
    public Object awaitedEvent(ProceedingJoinPoint call) throws Throwable {
        return ReportSteps.step("await user.created for user " + call.getArgs()[0], Map.of(), step -> {
            Object result = call.proceed();
            step.parameter("received", ReportSteps.describe(result));
            return result;
        });
    }

    private static Object asStep(ProceedingJoinPoint call) throws Throwable {
        CodeSignature signature = (CodeSignature) call.getSignature();
        String[] names = signature.getParameterNames();
        Object[] args = call.getArgs();
        Map<String, Object> parameters = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            parameters.put(names == null ? "arg" + i : names[i], args[i]);
        }
        String name = signature.getDeclaringType().getSimpleName() + "." + signature.getName();
        return ReportSteps.step(name, parameters, step -> {
            Object result = call.proceed();
            if (result != null) {
                step.parameter("result", ReportSteps.describe(result));
            }
            return result;
        });
    }
}
