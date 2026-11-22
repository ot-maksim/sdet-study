package com.study.project.assembly;

import org.springframework.boot.test.context.SpringBootTest;

/**
 * Where the project's Spring configuration is declared for every test class of a kind. In the JUnit twin each test
 * class carries {@code @SpringBootTest} through its kind annotation; here test classes carry no Spring annotation at
 * all, and {@link ProjectListener} asks Spring's {@code TestContextManager} for the context of this class instead.
 * One configuration → one cached context — one environment, one browser pool — for the whole run.
 */
@SpringBootTest(classes = ProjectConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
final class ProjectContext {

    private ProjectContext() {
    }
}
