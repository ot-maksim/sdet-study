package com.study.project;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.study.project.api.ApiTransport;
import com.study.project.api.ContractFilter;
import com.study.project.assembly.Groups;
import com.study.project.assembly.ProjectConfiguration;
import com.study.project.assembly.ProjectListener;
import com.study.project.events.EventClient;
import com.study.project.events.EventSubscription;
import com.study.project.ui.BrowserPool;
import com.study.project.ui.UiSession;
import com.study.project.ui.users.UsersPage;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * The layers of a one-module project as tests (ADR 0012): what separate Maven modules would make a compile error is a
 * red test here, plus what modules cannot say at all — where each tool may appear, what a test may touch.
 *
 * <pre>
 *   tests ──▶ assembly (kinds only) ─┐
 *     │                              ▼ wires everything
 *     └──▶ domain ──▶ api ─────────▶ core          report ──▶ domain, ui, api records (decorators)
 *            │ ──▶ db                 ▲             environment ──▶ api, db (probes)
 *            └──▶ ui                  │             events ──▶ api (the event payload is the API's user)
 * </pre>
 *
 * <p>The layer and tool rules apply to main code; test code is checked by the test rules below. Two kinds of test
 * classes live here: <b>assembly tests</b> (package {@code tests}, a kind group: {@code @Test(groups = Groups.API)})
 * that must speak the domain only, and <b>component tests</b> of the project's own parts ({@code ContractFilterTest},
 * {@code EventClientTest}, …) that test a tool seam and so use the tool. The generated client is main code of the api
 * layer.
 *
 * <p>The same rules as the JUnit twin's {@code @ArchTest} fields, run by TestNG: ArchUnit's JUnit engine is not on
 * this module's runner, so the rules are plain fields checked through the core API, one test invocation per field.
 */
public class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeJars())
            .importPackages("com.study.project");

    private static final DescribedPredicate<JavaClass> TEST_CODE = describe("are test code", type -> type.getSource()
            .map(source -> source.getUri().toString().contains("/test-classes/")).orElse(false));

    private static final DescribedPredicate<JavaClass> MAIN_CODE = describe("are main code", type -> !TEST_CODE.test(type));

    /**
     * Where assembly tests live: a package, not an annotation. A kind is a group, and a class without one would not
     * be "not an assembly test" but a broken one (no context, no beans) — the package keeps it under the rules.
     */
    private static final String TESTS = "com.study.project.tests..";

    private static final DescribedPredicate<JavaClass> ASSEMBLY_TESTS = describe("are assembly tests", type ->
            TEST_CODE.test(type) && type.getEnclosingClass().isEmpty() && type.getPackage().getName().startsWith("com.study.project.tests"));

    /** What ProjectListenerTest runs itself: test classes of a kind on purpose, never run by the suite. */
    private static final String FIXTURES = "com.study.project.assembly.fixtures..";

    // ---- layers ----------------------------------------------------------------------------------------------

    static final ArchRule layers = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Core").definedBy("com.study.project.core..")
            .layer("Api").definedBy("com.study.project.api..")
            .layer("Db").definedBy("com.study.project.db..")
            .layer("Ui").definedBy("com.study.project.ui..")
            .layer("Events").definedBy("com.study.project.events..")
            .layer("Domain").definedBy("com.study.project.domain..")
            .layer("Environment").definedBy("com.study.project.environment..")
            .layer("Report").definedBy("com.study.project.report..")
            .layer("Assembly").definedBy("com.study.project.assembly..")
            .whereLayer("Assembly").mayNotBeAccessedByAnyLayer()
            .whereLayer("Report").mayOnlyBeAccessedByLayers("Assembly")
            .whereLayer("Environment").mayOnlyBeAccessedByLayers("Assembly")
            .whereLayer("Events").mayOnlyBeAccessedByLayers("Assembly")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Report", "Assembly")
            .whereLayer("Ui").mayOnlyBeAccessedByLayers("Domain", "Report", "Assembly")
            .whereLayer("Db").mayOnlyBeAccessedByLayers("Domain", "Environment", "Assembly")
            .whereLayer("Api").mayOnlyBeAccessedByLayers("Domain", "Events", "Environment", "Report", "Assembly")
            .ignoreDependency(TEST_CODE, alwaysTrue())
            .because("channels (api, db, ui, events) know only core; domain relates them; only the root wires (ADR 0012)");

    /** Paths and models of the contract are an implementation detail of the api layer; callers get its records. */
    static final ArchRule generatedClientStaysInTheApiLayer = noClasses()
            .that().resideOutsideOfPackage("com.study.project.api..")
            .should().dependOnClassesThat().resideInAPackage("com.study.project.api.generated..")
            .because("a regenerated model must change UserApi only (ADR 0013)");

    // ---- tools: each in the package that owns it -------------------------------------------------------------

    static final ArchRule springOnlyInTheAssembly = onlyIn("org.springframework..", "com.study.project.assembly..")
            .because("the layers are plain objects wired by ProjectConfiguration (no scanning, no annotations)");

    /** The runner is the one thing the JUnit and the TestNG project differ in: it must stay this small. */
    static final ArchRule testngOnlyInTheAssembly = onlyIn("org.testng..", "com.study.project.assembly..")
            .because("the test runner is assembly glue; layers do not know which runner calls them (project/junit)");

    /** The parent pom puts JUnit Jupiter on the class path, Surefire runs the TestNG provider: JUnit would compile and never run. */
    static final ArchRule noJunitAnywhere = noClasses()
            .should().dependOnClassesThat().resideInAPackage("org.junit..")
            .because("this module runs on TestNG only: a JUnit @Test here compiles, is green in the IDE and never runs in the build");

    static final ArchRule testcontainersOnlyInTheEnvironment = onlyIn("org.testcontainers..", "com.study.project.environment..")
            .because("containers are one EnvironmentProvider of two");

    static final ArchRule allureOnlyInTheReport = onlyIn("io.qameta.allure..", "com.study.project.report..")
            .because("the report is observers, decorators and a filter around the code, never inside it");

    static final ArchRule kafkaOnlyInEventsAndTheEnvironment = noClasses().that(MAIN_CODE)
            .and().resideOutsideOfPackages("com.study.project.events..", "com.study.project.environment..")
            .should().dependOnClassesThat().resideInAPackage("org.apache.kafka..")
            .because("the broker is behind EventClient; the environment only checks it is there");

    static final ArchRule restAssuredOnlyInTheApiAndItsReport = noClasses().that(MAIN_CODE)
            .and().resideOutsideOfPackages("com.study.project.api..", "com.study.project.report..")
            .should().dependOnClassesThat().resideInAnyPackage("io.restassured..", "com.atlassian.oai..", "io.swagger..")
            .because("callers get ApiResponse and records; the report hands the transport a filter");

    static final ArchRule playwrightOnlyInTheUi = onlyIn("com.microsoft.playwright..", "com.study.project.ui..")
            .because("screens are interfaces; a native implementation would not be Playwright");

    static final ArchRule jdbcOnlyInTheDb = noClasses().that(MAIN_CODE)
            .and().resideOutsideOfPackage("com.study.project.db..")
            .should().dependOnClassesThat().resideInAnyPackage("java.sql..", "javax.sql..", "org.postgresql..")
            .because("columns and SQL live in table repositories");

    // ---- assembly tests ----------------------------------------------------------------------------------------

    /** Tests speak the domain: a change of transport, schema, markup, environment or report must not touch one. */
    static final ArchRule testsDoNotTouchTheTransportOrTheTools = noClasses().that(ASSEMBLY_TESTS)
            .should().dependOnClassesThat(resideInAnyPackage(
                    "io.restassured..", "com.atlassian.oai..", "io.swagger..", "com.microsoft.playwright..", "java.sql..", "javax.sql..",
                    "com.study.sut..", "com.study.project.db..", "com.study.project.api.generated..",
                    "com.study.project.environment..", "com.study.project.report..", "com.study.project.assembly.scope..",
                    "org.testcontainers..", "io.qameta.allure..", "org.apache.kafka..", "org.springframework..")
                    .or(belongToAnyOf(ApiTransport.class, ContractFilter.class, BrowserPool.class, UiSession.class,
                            UsersPage.class, EventClient.class, EventSubscription.class, ProjectConfiguration.class,
                            ProjectListener.class)))
            .because("a test gets everything as a parameter and talks to it in the domain's words; Spring in a test "
                    + "class (@TestPropertySource, @MockitoBean) is a second context, i.e. a second environment");

    static final ArchRule testsDoNotBuildTheGraph = noClasses().that(ASSEMBLY_TESTS)
            .should().callConstructorWhere(describe("the target is a project class that is not a record",
                    (JavaConstructorCall call) -> {
                        JavaClass owner = call.getTargetOwner();
                        return owner.getPackageName().startsWith("com.study.project")
                                && !owner.getName().startsWith(call.getOriginOwner().getName())
                                && owner.getRawSuperclass().map(s -> !s.isEquivalentTo(Record.class)).orElse(true);
                    }))
            .because("every service is made in ProjectConfiguration; records are values, not services");

    static final ArchRule testsDoNotInherit = classes().that(ASSEMBLY_TESTS)
            .should(new ArchCondition<>("extend only Object") {
                @Override
                public void check(JavaClass test, ConditionEvents events) {
                    test.getRawSuperclass().filter(s -> !s.isEquivalentTo(Object.class)).ifPresent(s ->
                            events.add(SimpleConditionEvent.violated(test, test.getName() + " extends " + s.getName())));
                }
            })
            .because("kinds are annotations, not base classes");

    /** The JUnit twin counts kind annotations; TestNG has no meta-annotations, so the kind is a group on the class. */
    static final ArchRule testsAreOfExactlyOneKind = classes().that(ASSEMBLY_TESTS)
            .should(new ArchCondition<>("have exactly one kind of " + Groups.KINDS + " in the class-level @Test, none on methods") {
                @Override
                public void check(JavaClass test, ConditionEvents events) {
                    long kinds = test.tryGetAnnotationOfType(Test.class)
                            .map(annotation -> Arrays.stream(annotation.groups()).filter(Groups.KINDS::contains).count())
                            .orElse(0L);
                    if (kinds != 1) {
                        events.add(SimpleConditionEvent.violated(test, test.getName() + " has " + kinds + " test kinds"
                                + (kinds == 0 ? " (no @Test(groups = Groups.X) on the class?)" : "")));
                    }
                    test.getMethods().forEach(method -> method.tryGetAnnotationOfType(Test.class)
                            .filter(annotation -> Arrays.stream(annotation.groups()).anyMatch(Groups.KINDS::contains))
                            .ifPresent(annotation -> events.add(SimpleConditionEvent.violated(method,
                                    method.getFullName() + " declares a kind: the kind is the class's"))));
                }
            })
            .because("the kind is the group for -Dgroups, what makes ProjectListener wire the class, and the "
                    + "capability check (events without a broker skip)");

    /**
     * New in the TestNG twin: one instance of a test class serves all its methods, and they run in parallel. A field
     * would be shared by concurrent tests (JUnit makes an instance per method, so the twin needs no such rule).
     */
    static final ArchRule testsHaveNoInstanceFields = classes().that(ASSEMBLY_TESTS)
            .should(new ArchCondition<>("declare no instance fields") {
                @Override
                public void check(JavaClass test, ConditionEvents events) {
                    test.getFields().stream()
                            .filter(field -> !field.getModifiers().contains(JavaModifier.STATIC))
                            .forEach(field -> events.add(SimpleConditionEvent.violated(field, field.getFullName()
                                    + " is shared by every method of the class, and they run at the same time")));
                }
            })
            .because("TestNG makes one instance per class for parallel methods: state lives in local variables, "
                    + "dependencies come as parameters");

    /** A component test with a kind would get the project's context and the environment it starts. */
    static final ArchRule kindsOnlyOnAssemblyTests = classes().that(TEST_CODE)
            .and().resideOutsideOfPackages(TESTS, FIXTURES)
            .should(new ArchCondition<>("have no kind group") {
                @Override
                public void check(JavaClass test, ConditionEvents events) {
                    test.tryGetAnnotationOfType(Test.class)
                            .filter(annotation -> Arrays.stream(annotation.groups()).anyMatch(Groups.KINDS::contains))
                            .ifPresent(annotation -> events.add(SimpleConditionEvent.violated(test,
                                    test.getName() + " has a kind outside the tests package")));
                }
            })
            .because("component tests own their dependencies; a kind means the project's Spring context and environment");

    @DataProvider(name = "rules")
    public static Object[][] rules() {
        return Arrays.stream(ArchitectureTest.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()) && ArchRule.class.isAssignableFrom(field.getType()))
                .map(field -> new Object[] {field.getName(), rule(field)})
                .toArray(Object[][]::new);
    }

    @Test(dataProvider = "rules")
    public void rule(String name, ArchRule rule) {
        rule.check(CLASSES);
    }

    private static ArchRule rule(Field field) {
        try {
            return (ArchRule) field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ArchRule onlyIn(String tool, String owner) {
        return noClasses().that(MAIN_CODE).and().resideOutsideOfPackage(owner)
                .should().dependOnClassesThat().resideInAPackage(tool);
    }
}
