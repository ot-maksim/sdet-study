package com.study.project.assembly;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Wires a test class to the project's Spring context. Every class with the same configuration shares one cached
 * context — one environment, one browser pool — for the whole run. Anything that changes the configuration of a
 * class ({@code @TestPropertySource}, {@code @MockitoBean}, another profile, {@code @DirtiesContext}) makes a
 * second context, and with it a second environment: keep test classes on the plain kinds.
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest(classes = ProjectConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ExtendWith(ProjectExtension.class)
public @interface ProjectTest {
}
