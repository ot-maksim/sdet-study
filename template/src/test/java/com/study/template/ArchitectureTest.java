package com.study.template;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.study.framework.api.ApiClient;
import com.study.framework.ui.BrowserPool;
import com.study.framework.ui.UiSession;
import com.study.framework.ui.users.UsersPage;
import com.study.template.events.EventClient;
import com.study.template.events.EventSubscription;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import org.junit.platform.commons.annotation.Testable;

/**
 * The layer rules of the template as tests — the same rules as {@code framework/tests}' {@code ArchitectureTest}
 * for the assembly tests, plus what stage 4 adds: Spring, Testcontainers, Kafka, Allure and AspectJ each stay in the
 * part of the module that owns them, and none of them reaches the stage 2 layers (CLAUDE.md).
 *
 * <p>Two kinds of test classes live in this module, and only one of them is subject to the test rules:
 * <ul>
 *   <li><b>assembly tests</b> — a kind ({@code @ApiTests}, …, all meta-annotated {@link TemplateTest}), the same
 *       bodies as in stage 2: they must not see the tools or build the graph;</li>
 *   <li><b>component tests</b> of the template's own parts ({@code EventClientTest}, {@code ReportingTest},
 *       {@code TemplateExtensionTest}, …): they test a tool seam, so they use the tool. Classes nested in a test
 *       class are its fixtures (the extension's tests break the rules there on purpose) and are not assembly
 *       tests either.</li>
 * </ul>
 * The tool rules apply to main code only, for the same reason.
 *
 * <p>Copied from stage 2 rather than shared: the two assemblies differ (Spring here), and a rules library for two
 * consumers would be the abstraction nobody needs yet (principles/kiss). ArchUnit reads bytecode: a type used only
 * by an unused local variable is not seen.
 */
@AnalyzeClasses(packages = {"com.study.framework", "com.study.template"})
class ArchitectureTest {

    private static final DescribedPredicate<JavaClass> MAIN_CODE = describe("are main code", type -> type.getSource()
            .map(source -> !source.getUri().toString().contains("/test-classes/")).orElse(true));

    private static final DescribedPredicate<JavaClass> ASSEMBLY_TESTS = describe("are assembly tests", type ->
            type.getEnclosingClass().isEmpty()
                    && type.isMetaAnnotatedWith(TemplateTest.class)
                    && type.getMethods().stream().anyMatch(method -> method.isMetaAnnotatedWith(Testable.class)));

    private static final List<Class<?>> TEST_KINDS =
            List.of(ApiTests.class, UiTests.class, E2eTests.class, EventTests.class);

    private static final String[] STAGE_4_TOOLS = {
            "org.springframework..", "org.testcontainers..", "io.qameta.allure..", "org.aspectj..", "org.apache.kafka.."};

    // ---- the module ------------------------------------------------------------------------------------------

    /**
     * The root package assembles; the parts do not know each other or the assembly (one exception: the environment
     * reads its settings from {@code TemplateProperties}); the stage 2 layers know none of it.
     */
    @ArchTest
    static final ArchRule parts = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Assembly").definedBy("com.study.template")
            .layer("Environment").definedBy("com.study.template.environment..")
            .layer("Events").definedBy("com.study.template.events..")
            .layer("Report").definedBy("com.study.template.report..")
            .layer("Scope").definedBy("com.study.template.scope..")
            .layer("Framework").definedBy("com.study.framework..")
            .whereLayer("Assembly").mayNotBeAccessedByAnyLayer()
            .whereLayer("Environment").mayOnlyBeAccessedByLayers("Assembly")
            .whereLayer("Events").mayOnlyBeAccessedByLayers("Assembly")
            .whereLayer("Report").mayOnlyBeAccessedByLayers("Assembly")
            .whereLayer("Scope").mayOnlyBeAccessedByLayers("Assembly")
            .whereLayer("Framework").mayOnlyBeAccessedByLayers("Assembly", "Environment", "Events", "Report", "Scope")
            .ignoreDependency(resideInAPackage("com.study.template.environment.."), belongToAnyOf(TemplateProperties.class))
            .because("only the root wires the parts; stage 2 is reused unchanged (ADR 0008)");

    /** Spring, Testcontainers, Allure, AspectJ and Kafka belong to stage 4: the layers of stage 2 stay free of them. */
    @ArchTest
    static final ArchRule frameworkIsFreeOfStage4Tools = noClasses().that().resideInAPackage("com.study.framework..")
            .should().dependOnClassesThat().resideInAnyPackage(STAGE_4_TOOLS)
            .because("the skeleton has DI by hand and no report tool; the tools are a property of the assembly (CLAUDE.md)");

    @ArchTest
    static final ArchRule springOnlyInTheAssemblyAndScope = noClasses().that(MAIN_CODE)
            .and().resideInAnyPackage("com.study.template.environment..", "com.study.template.events..",
                    "com.study.template.report..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..")
            .because("the parts are plain objects wired by TemplateConfiguration; only the root and the test scope "
                    + "speak Spring (ADR 0008)");

    @ArchTest
    static final ArchRule testcontainersOnlyInTheEnvironment = noClasses().that(MAIN_CODE)
            .and().resideOutsideOfPackage("com.study.template.environment..")
            .should().dependOnClassesThat().resideInAPackage("org.testcontainers..")
            .because("containers are one EnvironmentProvider of two (ADR 0009)");

    @ArchTest
    static final ArchRule reportToolsOnlyInTheReport = noClasses().that(MAIN_CODE)
            .and().resideOutsideOfPackage("com.study.template.report..")
            .should().dependOnClassesThat().resideInAnyPackage("io.qameta.allure..", "org.aspectj..")
            .because("the report is observers, decorators and an aspect around the code, never inside it (ADR 0011)");

    @ArchTest
    static final ArchRule kafkaOnlyInEventsAndTheEnvironment = noClasses().that(MAIN_CODE)
            .and().resideOutsideOfPackages("com.study.template.events..", "com.study.template.environment..")
            .should().dependOnClassesThat().resideInAPackage("org.apache.kafka..")
            .because("the broker is behind EventClient; the environment only checks it is there (ADR 0010)");

    // ---- assembly tests: the rules of stage 2, plus Spring ---------------------------------------------------

    /**
     * Assembly tests speak the domain, as in stage 2; events through {@code UserEvents}, not the client. No tool of
     * stage 4 either: the same bodies run on the hand-made root.
     */
    @ArchTest
    static final ArchRule testsDoNotTouchTheTransportOrTheTools = noClasses().that(ASSEMBLY_TESTS)
            .should().dependOnClassesThat(resideInAnyPackage(
                    "io.restassured..", "com.microsoft.playwright..", "java.sql..", "javax.sql..",
                    "com.study.sut..", "com.study.framework.db..",
                    "com.study.framework.core.config..", "com.study.framework.core.inject..",
                    "com.study.template.environment..", "com.study.template.report..", "com.study.template.scope..",
                    "org.testcontainers..", "io.qameta.allure..", "org.aspectj..", "org.apache.kafka..")
                    .or(belongToAnyOf(ApiClient.class, BrowserPool.class, UiSession.class, UsersPage.class,
                            EventClient.class, EventSubscription.class, TemplateConfiguration.class)))
            .because("a change of transport, schema, page markup, environment or report must not touch a test");

    /**
     * Spring in a test class — {@code @TestPropertySource}, {@code @MockitoBean}, {@code @ActiveProfiles},
     * {@code @DirtiesContext}, {@code @Autowired}, the context itself — changes or reaches into the context: a second
     * cached context is a second environment and a second browser pool ({@link TemplateTest}).
     */
    @ArchTest
    static final ArchRule testsDoNotTouchSpring = noClasses().that(ASSEMBLY_TESTS)
            .should().dependOnClassesThat().resideInAPackage("org.springframework..")
            .because("a test gets its context only through a kind; another configuration is another environment");

    @ArchTest
    static final ArchRule testsDoNotBuildTheGraph = noClasses().that(ASSEMBLY_TESTS)
            .should().callConstructorWhere(describe("the target is a framework or template class that is not a record",
                    (JavaConstructorCall call) -> {
                        JavaClass owner = call.getTargetOwner();
                        return (owner.getPackageName().startsWith("com.study.framework.")
                                || owner.getPackageName().startsWith("com.study.template"))
                                && !owner.getName().startsWith(call.getOriginOwner().getName())
                                && owner.getRawSuperclass().map(s -> !s.isEquivalentTo(Record.class)).orElse(true);
                    }))
            .because("every bean is made in TemplateConfiguration; records are values, not services");

    @ArchTest
    static final ArchRule testsDoNotInherit = classes().that(ASSEMBLY_TESTS)
            .should(new ArchCondition<>("extend only Object") {
                @Override
                public void check(JavaClass test, ConditionEvents events) {
                    test.getRawSuperclass().filter(s -> !s.isEquivalentTo(Object.class)).ifPresent(s ->
                            events.add(SimpleConditionEvent.violated(test,
                                    test.getName() + " extends " + s.getName())));
                }
            })
            .because("kinds are annotations (ADR 0007, 0008)");

    @ArchTest
    static final ArchRule testsAreOfExactlyOneKind = classes().that(ASSEMBLY_TESTS)
            .should(new ArchCondition<>("be annotated with exactly one of @ApiTests, @UiTests, @E2eTests, @EventTests") {
                @Override
                public void check(JavaClass test, ConditionEvents events) {
                    long kinds = TEST_KINDS.stream().filter(kind -> test.isAnnotatedWith(kind.getName())).count();
                    if (kinds != 1) {
                        events.add(SimpleConditionEvent.violated(test, test.getName() + " has " + kinds
                                + " test kinds" + (kinds == 0 ? " (@TemplateTest directly?)" : "")));
                    }
                }
            })
            .because("the kind is the tag for -Dgroups and the capability check (events without a broker skip)");
}
