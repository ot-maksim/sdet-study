package com.study.project.assembly;

import com.study.project.assembly.scope.TestBeans;
import com.study.project.assembly.scope.TestScope;
import com.study.project.environment.EnvironmentProvider;
import com.study.project.report.Artifact;
import com.study.project.report.Artifacts;
import com.study.project.ui.UiSession;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestContextManager;
import org.testng.IAnnotationTransformer;
import org.testng.IDataProviderMethod;
import org.testng.IHookCallBack;
import org.testng.IHookable;
import org.testng.IRetryAnalyzer;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.ITestAnnotation;
import org.testng.xml.XmlTest;

/**
 * The runner glue: what JUnit's {@code SpringExtension} + the twin's {@code ProjectExtension} do, as one TestNG
 * listener. Registered through {@code META-INF/services/org.testng.ITestNGListener}, so it is active in every TestNG
 * run — Surefire with {@code testng.xml}, one class from the IDE, a programmatic {@code TestNG} — not only where a
 * suite file lists it. A class is a <b>project test</b> when its class-level {@code @Test} has a kind of
 * {@link Groups}; everything else (component tests) is left alone.
 *
 * <ul>
 *   <li><b>the Spring context without a base class</b>: Spring's official TestNG support is the base class
 *       {@code AbstractTestNGSpringContextTests}; here the listener asks a {@link TestContextManager} of
 *       {@link ProjectContext} for the cached context, so test classes extend nothing and carry no Spring;</li>
 *   <li><b>parameters without {@code @Autowired}</b>: TestNG injects only its own types into a test method. The
 *       annotation transformer gives a project test with parameters the data provider {@value #BEANS}, which
 *       supplies one row of {@code null}s, i.e. "invoke once"; the hook then fills them with beans, so a test lists
 *       what it needs exactly as in the JUnit twin;</li>
 *   <li><b>the test scope</b> — the reason for {@link IHookable}: TestNG makes <i>one</i> instance of a test class
 *       for all its methods and runs them in parallel ({@code parallel="methods"}). A test-scoped bean in a field
 *       would be shared by concurrent methods; there are no fields (ArchitectureTest). The hook runs <i>around</i>
 *       each invocation on its own thread: it binds the test only while resolving the parameters
 *       ({@link TestScope#within}, as in the twin), runs the method, and closes the test's beans and undo actions
 *       after it, also when it failed;</li>
 *   <li><b>environment policy</b>: unavailable → {@link SkipException} with the reason, or a failure when
 *       {@code project.require-environment=true} (CI); for {@link Groups#EVENTS} also whether there is a broker;</li>
 *   <li><b>parallelism within the browser pool</b>: a {@code <test>} with more threads than
 *       {@code project.ui.max-browsers} fails its project tests before they wait for a browser and time out;</li>
 *   <li><b>a screenshot of a failed test</b>, only if it had a browser, while the browser is still the test's —
 *       {@code ITestListener.onTestFailure} would come after the hook returned it to the pool;</li>
 *   <li><b>retry in CI</b> ({@link ProjectRetry}) and the list of flaky tests: the system property
 *       {@value #FLAKY_REPORT} names the file (Surefire sets it); without it nothing is written.</li>
 * </ul>
 *
 * <p>Order matters once: {@link #onTestStart} must run before Allure's listener, which copies the parameters into the
 * report. ServiceLoader finds this module's classes before the jars, and TestNG calls listeners in that order.
 */
public final class ProjectListener implements IAnnotationTransformer, IHookable, ITestListener {

    /** The data provider every project test with parameters gets: one invocation, parameters filled by the hook. */
    public static final String BEANS = "project-beans";

    /** System property: the file that lists tests that passed only on a retry. */
    public static final String FLAKY_REPORT = "flaky.report";

    private final Map<Class<?>, TestContextManager> spring = new ConcurrentHashMap<>();
    private final Map<Class<?>, Optional<String>> environment = new ConcurrentHashMap<>();
    private final int retries;
    private final Optional<Path> flakyReport;

    /** What ServiceLoader calls: the build's settings, from Surefire's system properties. */
    public ProjectListener() {
        this(ProjectRetry.attempts(), Optional.ofNullable(System.getProperty(FLAKY_REPORT)).map(Path::of));
    }

    /** A listener of its own for a programmatic run in a test: the build's retries and flaky list stay the build's. */
    ProjectListener(int retries, Optional<Path> flakyReport) {
        this.retries = retries;
        this.flakyReport = flakyReport;
    }

    // ---- before the run: how TestNG reads the annotations ----------------------------------------------------

    @Override
    public void transform(ITestAnnotation annotation, Class testClass, Constructor testConstructor, Method testMethod) {
        if (testMethod == null) {
            return;   // the class-level @Test: its groups are the kind, nothing to change
        }
        if (retries > 0 && noRetryAnalyzer(annotation.getRetryAnalyzerClass())) {
            annotation.setRetryAnalyzer(ProjectRetry.class);
        }
        if (testMethod.getParameterCount() > 0 && annotation.getDataProvider().isEmpty()
                && !Groups.kindsOf(testMethod.getDeclaringClass()).isEmpty()) {
            annotation.setDataProvider(BEANS);
            annotation.setDataProviderClass(ProjectListener.class);
        }
    }

    /** One row, one {@code null} per parameter: TestNG checks the count, the hook supplies the values. */
    @DataProvider(name = BEANS)
    public static Object[][] beans(Method method) {
        return new Object[][] {new Object[method.getParameterCount()]};
    }

    // ---- around every test method ------------------------------------------------------------------------------

    /**
     * The report names a test by its method, not by its data provider's row: Surefire would print
     * {@code registers[null, null](1)} and Allure {@code arg0 = null}. Only the result's copy is emptied; TestNG
     * invokes the method with its own array, which the hook fills.
     */
    @Override
    public void onTestStart(ITestResult result) {
        if (isOurs(result.getMethod().getDataProviderMethod())) {
            result.setParameters(new Object[0]);
        }
    }

    @Override
    public void run(IHookCallBack callBack, ITestResult result) {
        Class<?> testClass = result.getTestClass().getRealClass();
        Set<String> kinds = Groups.kindsOf(testClass);
        if (kinds.isEmpty()) {
            callBack.runTestMethod(result);   // a component test: plain TestNG
            return;
        }
        ApplicationContext context = context(testClass);
        checkParallelism(result.getTestContext().getCurrentXmlTest(), context);
        checkEnvironment(testClass, kinds.contains(Groups.EVENTS), context);

        TestBeans beans = new TestBeans(testClass.getSimpleName() + "." + result.getMethod().getMethodName());
        Object[] parameters = callBack.getParameters();
        Throwable failure = null;
        try {
            if (isOurs(result.getMethod().getDataProviderMethod())) {
                resolve(result.getMethod().getConstructorOrMethod().getMethod(), parameters, beans, context);
            }
            callBack.runTestMethod(result);
            failure = result.getThrowable();   // TestNG rethrows it after this hook
            if (failure != null) {
                screenshot(beans, result, context);
            }
        } catch (RuntimeException | Error e) {
            failure = e;
            throw e;
        } finally {
            Arrays.fill(parameters, null);   // no bean outlives its test through TestNG's array (a retry fills it again)
            close(beans, failure);
        }
    }

    private static void resolve(Method method, Object[] parameters, TestBeans beans, ApplicationContext context) {
        Class<?>[] types = method.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];
            try {
                parameters[i] = TestScope.within(beans, () -> context.getBean(type));
            } catch (RuntimeException e) {
                throw new IllegalStateException("Cannot resolve " + type.getSimpleName() + " for " + method.getName()
                        + ": " + rootMessage(e), e);
            }
        }
    }

    /** Ends the test: a cleanup failure fails a passed test, and is added to the failure of a failed one. */
    private static void close(TestBeans beans, Throwable failure) {
        try {
            beans.close();
        } catch (RuntimeException e) {
            if (failure == null) {
                throw e;
            }
            failure.addSuppressed(e);
        }
    }

    private ApplicationContext context(Class<?> testClass) {
        Class<?> configured = declaresSpring(testClass) ? testClass : ProjectContext.class;
        return spring.computeIfAbsent(configured, TestContextManager::new).getTestContext().getApplicationContext();
    }

    /**
     * A test class with Spring annotations of its own ({@code @TestPropertySource}, {@code @Import}) gets its own
     * context — and with it a second environment. Only the listener's tests do that on purpose (their fixtures);
     * ArchitectureTest forbids Spring in project tests.
     */
    private static boolean declaresSpring(Class<?> testClass) {
        return Arrays.stream(testClass.getAnnotations())
                .anyMatch(annotation -> annotation.annotationType().getPackageName().startsWith("org.springframework"));
    }

    private static void checkParallelism(XmlTest test, ApplicationContext context) {
        int maxBrowsers = context.getBean(ProjectProperties.class).ui().maxBrowsers();
        if (test.getParallel().isParallel() && test.getThreadCount() > maxBrowsers) {
            throw new IllegalStateException("<test name=\"" + test.getName() + "\"> runs up to " + test.getThreadCount()
                    + " tests at once, the browser pool has " + maxBrowsers + " (project.ui.max-browsers): UI tests"
                    + " would wait and time out. Lower thread-count in testng.xml or raise project.ui.max-browsers");
        }
    }

    /** Asked once per test class, as the twin's ExecutionCondition; every method of the class gets the answer. */
    private void checkEnvironment(Class<?> testClass, boolean events, ApplicationContext context) {
        Optional<String> reason = environment.computeIfAbsent(testClass, key -> {
            EnvironmentProvider provider = context.getBean(EnvironmentProvider.class);
            Optional<String> unavailable = provider.unavailable();
            return unavailable.isEmpty() && events ? provider.eventsUnavailable() : unavailable;
        });
        if (reason.isEmpty()) {
            return;
        }
        if (context.getBean(ProjectProperties.class).requireEnvironment()) {
            throw new IllegalStateException(reason.get() + " (project.require-environment=true)");
        }
        throw new SkipException(reason.get() + " (set project.require-environment=true to fail instead)");
    }

    private static void screenshot(TestBeans beans, ITestResult result, ApplicationContext context) {
        beans.peek(UiSession.class).ifPresent(session -> {
            String test = result.getTestClass().getRealClass().getSimpleName() + "-" + result.getMethod().getMethodName();
            try {
                byte[] png = screenshot(session);
                context.getBean(Artifacts.class).publish(new Artifact(test, "screenshot", "image/png", "png", png));
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

    // ---- after a test --------------------------------------------------------------------------------------------

    @Override
    public void onTestSuccess(ITestResult result) {
        IRetryAnalyzer retry = result.getMethod().getRetryAnalyzer(result);
        if (!(retry instanceof ProjectRetry projectRetry) || !projectRetry.wasRetried()) {
            return;
        }
        System.err.println("FLAKY: " + name(result) + " passed only on a retry");
        flakyReport.ifPresent(file -> append(file, name(result)));
    }

    private static synchronized void append(Path file, String line) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String name(ITestResult result) {
        return result.getTestClass().getRealClass().getName() + "." + result.getMethod().getMethodName();
    }

    private static boolean isOurs(IDataProviderMethod provider) {
        return provider != null && BEANS.equals(provider.getName());
    }

    /** TestNG's default is a no-op analyzer class, not {@code null}. */
    private static boolean noRetryAnalyzer(Class<? extends IRetryAnalyzer> analyzer) {
        return analyzer == null || analyzer.getSimpleName().equals("DisabledRetryAnalyzer");
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
