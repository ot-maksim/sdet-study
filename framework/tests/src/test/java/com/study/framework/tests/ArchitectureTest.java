package com.study.framework.tests;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.study.framework.api.ApiClient;
import com.study.framework.ui.BrowserPool;
import com.study.framework.ui.UiSession;
import com.study.framework.ui.users.UsersPage;
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
 * The layer rules of the framework as tests (docs/test-framework-architecture.md, structural rules 1–3).
 *
 * <p>Maven modules already forbid a wrong <i>module</i> dependency — but only as long as nobody adds one to a
 * {@code pom.xml}, and they say nothing about what code <i>inside</i> a module may touch: this module sees every
 * layer and, transitively, RestAssured, Playwright and JDBC. The rules below state both in one place.
 *
 * <p>"A test" is a class with a JUnit test method ({@code @Test}, {@code @ParameterizedTest}, … — all are
 * meta-annotated {@link Testable}). The rules here are fields, so this class is not one of them.
 *
 * <p>ArchUnit reads bytecode: a type used only for an unused local variable leaves no trace there and is not
 * seen; a call, a field, a parameter or a {@code new} is.
 */
@AnalyzeClasses(packages = "com.study.framework")
class ArchitectureTest {

    private static final DescribedPredicate<JavaClass> TESTS = describe("are tests", type -> type.getMethods()
            .stream().anyMatch(method -> method.isMetaAnnotatedWith(Testable.class)));

    private static final List<Class<?>> TEST_KINDS = List.of(ApiTests.class, UiTests.class, E2eTests.class);

    /** core ← clients (api, db, ui; not each other) ← domain ← tests: who may use whom. */
    @ArchTest
    static final ArchRule layers = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Core").definedBy("com.study.framework.core..")
            .layer("Api").definedBy("com.study.framework.api..")
            .layer("Db").definedBy("com.study.framework.db..")
            .layer("Ui").definedBy("com.study.framework.ui..")
            .layer("Domain").definedBy("com.study.framework.domain..")
            .layer("Tests").definedBy("com.study.framework.tests..")
            .whereLayer("Tests").mayNotBeAccessedByAnyLayer()
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Tests")
            .whereLayer("Api").mayOnlyBeAccessedByLayers("Domain", "Tests")
            .whereLayer("Db").mayOnlyBeAccessedByLayers("Domain", "Tests")
            .whereLayer("Ui").mayOnlyBeAccessedByLayers("Domain", "Tests")
            .whereLayer("Core").mayOnlyBeAccessedByLayers("Api", "Db", "Ui", "Domain", "Tests")
            .because("dependencies point down to core only (ADR 0001, 0006, 0007)");

    /**
     * Tests speak the domain: steps, checks, the screen as a person uses it, the API as a contract. The transport,
     * the table and the concrete page object belong to the layers below; the storage is checked through
     * {@code UserChecks}, never by SQL in a test.
     */
    @ArchTest
    static final ArchRule testsDoNotTouchTheTransport = noClasses().that(TESTS)
            .should().dependOnClassesThat(resideInAnyPackage(
                    "io.restassured..", "com.microsoft.playwright..", "java.sql..", "javax.sql..",
                    "com.study.sut..", "com.study.framework.db..",
                    "com.study.framework.core.config..", "com.study.framework.core.inject..")
                    .or(belongToAnyOf(ApiClient.class, BrowserPool.class, UiSession.class, UsersPage.class)))
            .because("a change of transport, schema or page markup must not touch a test (locality of change); "
                    + "tests depend on UsersScreen, not on its implementation (DIP)");

    /** The object graph is built by the composition root; a test only declares parameters and makes values. */
    @ArchTest
    static final ArchRule testsDoNotBuildTheGraph = noClasses().that(TESTS)
            .should().callConstructorWhere(describe("the target is a class of a layer below that is not a record",
                    (JavaConstructorCall call) -> {
                        JavaClass owner = call.getTargetOwner();
                        return owner.getPackageName().startsWith("com.study.framework.")
                                && !owner.getPackageName().startsWith("com.study.framework.tests")
                                && owner.getRawSuperclass().map(s -> !s.isEquivalentTo(Record.class)).orElse(true);
                    }))
            .because("every `new` of the framework is in SutComposition (ADR 0001); records are values, not services");

    /** A test kind is an annotation, not a base class: no inherited state, no god BaseTest (review P01). */
    @ArchTest
    static final ArchRule testsDoNotInherit = classes().that(TESTS)
            .should(new ArchCondition<>("extend only Object") {
                @Override
                public void check(JavaClass test, ConditionEvents events) {
                    test.getRawSuperclass().filter(s -> !s.isEquivalentTo(Object.class)).ifPresent(s ->
                            events.add(SimpleConditionEvent.violated(test,
                                    test.getName() + " extends " + s.getName())));
                }
            })
            .because("kinds are annotations (ADR 0007)");

    /** Exactly one kind: it is what wires the class to the root and tags it for -Dgroups. */
    @ArchTest
    static final ArchRule testsAreOfExactlyOneKind = classes().that(TESTS)
            .should(new ArchCondition<>("be annotated with exactly one of @ApiTests, @UiTests, @E2eTests") {
                @Override
                public void check(JavaClass test, ConditionEvents events) {
                    long kinds = TEST_KINDS.stream().filter(kind -> test.isAnnotatedWith(kind.getName())).count();
                    if (kinds != 1) {
                        events.add(SimpleConditionEvent.violated(test,
                                test.getName() + " has " + kinds + " test kinds"));
                    }
                }
            })
            .because("without a kind the parameters cannot be resolved; with two the class is two kinds by accident");
}
