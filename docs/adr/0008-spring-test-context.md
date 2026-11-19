# ADR 0008 — Spring Boot test context as the composition root

Status: accepted (stage 4a). Supersedes nothing: ADR 0001 stays the skeleton's choice; this is the working template's.

## Context

Stage 2 built the object graph by hand (`CompositionRoot` + `Bindings`, ADR 0001): about 150 lines of core
mechanics, every `new` in `SutComposition`, RUN/TEST lifetimes, guards against captive dependencies and test
objects in `@BeforeAll`. A team template needs what that mechanics does not have: layered configuration with
profiles out of the box, a container the next hire already knows, and a place for integrations (Testcontainers,
Kafka, Allure) that expect Spring. The question is what moves to Spring and what we refuse to lose.

## Decision

- A new module `template/` assembles the **unchanged** stage 2 layers (`framework-domain` and below). They get no
  Spring annotations; only the assembly is replaced. The three stage 2 test classes moved with one changed line
  (`package`).
- The root is one `@Configuration` class, `TemplateConfiguration`: one `@Bean` per binding, **no component scanning,
  no auto-configuration**, `proxyBeanMethods = false`, all beans lazy (`spring.main.lazy-initialization`).
- `@SpringBootTest(classes = TemplateConfiguration.class)` behind the same test-kind annotations
  (`@ApiTests`/`@UiTests`/`@E2eTests` → `@TemplateTest`); one cached context for the whole run.
- What Spring lacks is added explicitly, not given up:
  - a `test` scope (`TestScope` + `@TestScoped`): one instance per test method, destroyed after it; bean
    destruction and the steps' undo actions share one reverse order (core `Scope`);
  - the scope is bound to the thread **only while a test's parameters are resolved**, so a field, `@BeforeAll`,
    a singleton or another thread asking for a test bean fails with one message instead of getting a wrong instance;
  - `CaptiveDependencyGuard`: a singleton that depends on a test-scoped bean fails at creation;
  - `TemplateExtension`: test parameters without `@Autowired`, environment policy (skip/fail), screenshot on failure.
- Configuration: `TemplateProperties` (`@ConfigurationProperties`, records, `ignoreUnknownFields = false`) +
  `application.yml` + profiles as slices (`stand`, `ci`, `mobile`, `db`); passwords bind straight into `Secret`.

## Alternatives

| Option | Why not |
|---|---|
| Keep the hand-written root | Fine for the skeleton (ADR 0001); a team template would re-implement profiles, env binding, integrations |
| Component scanning + `@Component` on clients and steps | Spring leaks into every layer; the graph is no longer readable in one place; scanning order surprises |
| Auto-configuration (`@SpringBootApplication`) | Brings DataSource/Jackson/etc. auto-config we do not use; the context depends on what is on the class path |
| Test objects as prototypes | No end of life: a prototype is never destroyed, browsers and users leak |
| Scoped proxies (`ScopedProxyMode.TARGET_CLASS`) for test beans | Hides captive dependencies instead of reporting them; every call goes through a lookup |
| Thread scope bound for the whole test (Cucumber glue style) | `ThreadLocal` pitfalls: parallel branches of a step silently get another instance (principles/parallel) |
| Spring Boot 4 | Requires JUnit 6; the repo is on JUnit 5.14. Boot 3.5 = Spring 6.2 + JUnit 5 |

## Consequences

- \+ Profiles, env vars, relaxed binding, typed records for free; `-Dspring.profiles.active=stand,mobile` replaces
  `env/*.properties` + several `-D`.
- \+ The layers are proven DI-agnostic: the same classes run under two roots.
- \+ Spring's context cache gives "one environment per run" without static state.
- − Startup: the context starts in ~0.5 s (stage 2: none); negligible next to containers, visible in unit-sized runs.
- − Three Spring-specific traps had to be closed by hand (no test scope, unannotated parameters, inferred destroy
  methods — `Cleanup` needs `destroyMethod = ""`); a team must know why those classes exist.
- − A class that changes the configuration (`@TestPropertySource`, `@MockitoBean`, another profile) creates a second
  context and a second environment; the test kinds discourage it, nothing forbids it.
- − Unknown keys fail only under `template.*`; Spring's own keys (`spring.*`, `logging.*`) are still lenient.
