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
import com.study.project.assembly.ApiTests;
import com.study.project.assembly.ContractTests;
import com.study.project.assembly.E2eTests;
import com.study.project.assembly.EventTests;
import com.study.project.assembly.ProjectConfiguration;
import com.study.project.assembly.ProjectExtension;
import com.study.project.assembly.ProjectTest;
import com.study.project.assembly.UiTests;
import com.study.project.events.EventClient;
import com.study.project.events.EventSubscription;
import com.study.project.ui.BrowserPool;
import com.study.project.ui.UiSession;
import com.study.project.ui.users.UsersPage;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import org.junit.platform.commons.annotation.Testable;

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
 * classes live here: <b>assembly tests</b> (a kind: {@code @ApiTests}, …) that must speak the domain only, and
 * <b>component tests</b> of the project's own parts ({@code ContractFilterTest}, {@code EventClientTest}, …) that
 * test a tool seam and so use the tool. The generated client is main code of the api layer.
 */
@AnalyzeClasses(packages = "com.study.project", importOptions = ImportOption.DoNotIncludeJars.class)
class ArchitectureTest {

    private static final DescribedPredicate<JavaClass> TEST_CODE = describe("are test code", type -> type.getSource()
            .map(source -> source.getUri().toString().contains("/test-classes/")).orElse(false));

    private static final DescribedPredicate<JavaClass> MAIN_CODE = describe("are main code", type -> !TEST_CODE.test(type));

    private static final DescribedPredicate<JavaClass> ASSEMBLY_TESTS = describe("are assembly tests", type ->
            type.getEnclosingClass().isEmpty()
                    && type.isMetaAnnotatedWith(ProjectTest.class)
                    && type.getMethods().stream().anyMatch(method -> method.isMetaAnnotatedWith(Testable.class)));

    private static final List<Class<?>> TEST_KINDS = List.of(ApiTests.class, ContractTests.class, UiTests.class, E2eTests.class, EventTests.class);

    // ---- layers ----------------------------------------------------------------------------------------------

    @ArchTest
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
    @ArchTest
    static final ArchRule generatedClientStaysInTheApiLayer = noClasses()
            .that().resideOutsideOfPackage("com.study.project.api..")
            .should().dependOnClassesThat().resideInAPackage("com.study.project.api.generated..")
            .because("a regenerated model must change UserApi only (ADR 0013)");

    // ---- tools: each in the package that owns it -------------------------------------------------------------

    @ArchTest
    static final ArchRule springOnlyInTheAssembly = onlyIn("org.springframework..", "com.study.project.assembly..")
            .because("the layers are plain objects wired by ProjectConfiguration (no scanning, no annotations)");

    /** The runner is the one thing the JUnit and the TestNG project differ in: it must stay this small. */
    @ArchTest
    static final ArchRule junitOnlyInTheAssembly = onlyIn("org.junit..", "com.study.project.assembly..")
            .because("the test runner is assembly glue; layers do not know which runner calls them (project/testng)");

    @ArchTest
    static final ArchRule testcontainersOnlyInTheEnvironment = onlyIn("org.testcontainers..", "com.study.project.environment..")
            .because("containers are one EnvironmentProvider of two");

    @ArchTest
    static final ArchRule allureOnlyInTheReport = onlyIn("io.qameta.allure..", "com.study.project.report..")
            .because("the report is observers, decorators and a filter around the code, never inside it");

    @ArchTest
    static final ArchRule kafkaOnlyInEventsAndTheEnvironment = noClasses().that(MAIN_CODE)
            .and().resideOutsideOfPackages("com.study.project.events..", "com.study.project.environment..")
            .should().dependOnClassesThat().resideInAPackage("org.apache.kafka..")
            .because("the broker is behind EventClient; the environment only checks it is there");

    @ArchTest
    static final ArchRule restAssuredOnlyInTheApiAndItsReport = noClasses().that(MAIN_CODE)
            .and().resideOutsideOfPackages("com.study.project.api..", "com.study.project.report..")
            .should().dependOnClassesThat().resideInAnyPackage("io.restassured..", "com.atlassian.oai..")
            .because("callers get ApiResponse and records; the report hands the transport a filter");

    @ArchTest
    static final ArchRule playwrightOnlyInTheUi = onlyIn("com.microsoft.playwright..", "com.study.project.ui..")
            .because("screens are interfaces; a native implementation would not be Playwright");

    @ArchTest
    static final ArchRule jdbcOnlyInTheDb = noClasses().that(MAIN_CODE)
            .and().resideOutsideOfPackage("com.study.project.db..")
            .should().dependOnClassesThat().resideInAnyPackage("java.sql..", "javax.sql..", "org.postgresql..")
            .because("columns and SQL live in table repositories");

    // ---- assembly tests ----------------------------------------------------------------------------------------

    /** Tests speak the domain: a change of transport, schema, markup, environment or report must not touch one. */
    @ArchTest
    static final ArchRule testsDoNotTouchTheTransportOrTheTools = noClasses().that(ASSEMBLY_TESTS)
            .should().dependOnClassesThat(resideInAnyPackage(
                    "io.restassured..", "com.atlassian.oai..", "com.microsoft.playwright..", "java.sql..", "javax.sql..",
                    "com.study.sut..", "com.study.project.db..", "com.study.project.api.generated..",
                    "com.study.project.environment..", "com.study.project.report..", "com.study.project.assembly.scope..",
                    "org.testcontainers..", "io.qameta.allure..", "org.apache.kafka..", "org.springframework..")
                    .or(belongToAnyOf(ApiTransport.class, ContractFilter.class, BrowserPool.class, UiSession.class,
                            UsersPage.class, EventClient.class, EventSubscription.class, ProjectConfiguration.class,
                            ProjectExtension.class)))
            .because("a test gets everything as a parameter and talks to it in the domain's words; Spring in a test "
                    + "class (@TestPropertySource, @MockitoBean) is a second context, i.e. a second environment");

    @ArchTest
    static final ArchRule testsDoNotBuildTheGraph = noClasses().that(ASSEMBLY_TESTS)
            .should().callConstructorWhere(describe("the target is a project class that is not a record",
                    (JavaConstructorCall call) -> {
                        JavaClass owner = call.getTargetOwner();
                        return owner.getPackageName().startsWith("com.study.project")
                                && !owner.getName().startsWith(call.getOriginOwner().getName())
                                && owner.getRawSuperclass().map(s -> !s.isEquivalentTo(Record.class)).orElse(true);
                    }))
            .because("every service is made in ProjectConfiguration; records are values, not services");

    @ArchTest
    static final ArchRule testsDoNotInherit = classes().that(ASSEMBLY_TESTS)
            .should(new ArchCondition<>("extend only Object") {
                @Override
                public void check(JavaClass test, ConditionEvents events) {
                    test.getRawSuperclass().filter(s -> !s.isEquivalentTo(Object.class)).ifPresent(s ->
                            events.add(SimpleConditionEvent.violated(test, test.getName() + " extends " + s.getName())));
                }
            })
            .because("kinds are annotations, not base classes");

    @ArchTest
    static final ArchRule testsAreOfExactlyOneKind = classes().that(ASSEMBLY_TESTS)
            .should(new ArchCondition<>("be annotated with exactly one of @ApiTests, @ContractTests, @UiTests, @E2eTests, @EventTests") {
                @Override
                public void check(JavaClass test, ConditionEvents events) {
                    long kinds = TEST_KINDS.stream().filter(kind -> test.isAnnotatedWith(kind.getName())).count();
                    if (kinds != 1) {
                        events.add(SimpleConditionEvent.violated(test, test.getName() + " has " + kinds
                                + " test kinds" + (kinds == 0 ? " (@ProjectTest directly?)" : "")));
                    }
                }
            })
            .because("the kind is the tag for -Dgroups and the capability check (events without a broker skip)");

    private static ArchRule onlyIn(String tool, String owner) {
        return noClasses().that(MAIN_CODE).and().resideOutsideOfPackage(owner)
                .should().dependOnClassesThat().resideInAPackage(tool);
    }
}
