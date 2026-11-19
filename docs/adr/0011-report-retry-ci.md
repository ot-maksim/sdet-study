# ADR 0011 — Report, retry, parallel run and CI of the template

Status: accepted (stage 4c).

## Context

The template ran green or red with nothing in between: no report a person reads after a CI run, a screenshot
path printed to `System.err`, no policy for a test that fails once in twenty runs, a parallelism that was correct
only by a comment, and no pipeline. Two constraints shape every answer: the stage 2 layers stay unchanged and
free of Allure (CLAUDE.md), and their classes `UserSteps`, `ApiClient`, `UserApi` are **final with no interface** —
there is no seam to wrap.

## Decision

- **Allure 2.35** (`allure-junit-platform`; the 3.x line needs JUnit 6). Its listener is the JUnit Platform's
  Observer, registered by `ServiceLoader`: the engine is the subject, nothing calls Allure from tests.
- **Artifacts — an Observer of our own.** `TemplateExtension` publishes a screenshot of a failed UI test to
  `report.Artifacts`; every `ArtifactListener` bean is subscribed (`ArtifactFiles` → `target/artifacts`,
  `AllureArtifacts` → an attachment of the running test). A failing listener is isolated and printed.
- **Steps where there is a seam — decorators**, wired in `TemplateConfiguration`: `ReportingUserPrecondition`,
  `ReportingUsersScreen` (the latter also wraps stage 4d's native screen).
- **Steps where there is none — an aspect woven at load time** (`report.ReportingAspect`, `META-INF/aop.xml`,
  `-javaagent:aspectjweaver` in Surefire's `argLine`): every public method of `UserSteps`, the private
  `ApiClient.send` (request body + response as attachments, status in the step name), `UserEvents.created`.
  Weaving is limited to `com.study..*`. `ReportingTest` fails the build when the agent is missing or a pointcut
  stops producing its step.
- **Retry only in CI, only once, visibly.** Surefire `rerunFailingTestsCount=1` in the Maven profile `ci`, active
  when `CI` is set (GitHub Actions sets it). A test green on the second attempt is a *flake*: Surefire prints
  `Flakes:` and writes `flakyFailure` to the XML, Allure keeps both attempts under one `historyId` (Retries tab,
  flaky mark), the pipeline lists flakes in the job summary and as warnings. Locally nothing is retried.
- **Parallel run — checked, not commented.** `TemplateExtension` refuses to start when JUnit may run more tests
  than `template.ui.max-browsers` (fixed or dynamic strategy, read from the same keys JUnit reads). One fork
  (`forkCount=1`): a fork is another context, environment and browser pool. A class that must run alone says so
  with `@Isolated` (`ReportingTest`).
- **CI — GitHub Actions** (`.github/workflows/tests.yml`): JDK 17, Chromium with system libraries (cached), the
  compose stand for stage 2 (`stand.required=true`), template on its own containers (Spring profile `ci`), the whole
  reactor with `mvn --fail-at-end verify`; then the Allure report (`allure-maven`, report 2.36) with history restored from the
  published site; uploaded as an artifact on every run and deployed to GitHub Pages from `main`, red runs included.

## Alternatives

| Option | Why not |
|---|---|
| Allure in `framework/` (`@Step` on `UserSteps`, `AllureRestAssured` in `ApiClient`) | The skeleton would depend on a report tool; the agreed constraint is that it does not |
| A neutral `StepListener` port in `framework/core`, Allure behind it in the template | The cleanest long-term seam, but changes stage 2 for a stage 4 concern; the right move if the framework were ours to evolve |
| Extract interfaces in `framework/` and decorate everything | Explicit, but changes stage 2 and doubles the types for one consumer |
| Spring AOP (proxies) | Proxies only beans and only non-final classes through their public methods; `UserSteps` is final, `ApiClient.send` private |
| Compile-time weaving (`aspectj-maven-plugin`) of the framework jars | Weaves a copy of other modules' classes into this one; a plugin that lags behind JDK releases |
| A global RestAssured filter (`RestAssured.filters(...)`) | Static state — what stage 2 removed (principles/parallel); misses nothing, but changes every client in the JVM |
| JUnit Pioneer `@RetryingTest` | Per test, decided by whoever wrote the test; hides a flaky test in an annotation, retries locally too |
| Retry everywhere (also local runs), or more than once | A flake becomes invisible where it can be fixed cheapest; two retries turn a 1-in-3 failure green |
| A `gh-pages` branch + a third-party Allure action | History as commits nobody reads, another action's permissions; Pages from an artifact needs neither |
| Allure 3 (npm `allure`) for the report | Reads the same results; history in another format, the Maven plugin targets Allure 2. Not needed yet |

## Consequences

- \+ A failed run explains itself: steps of the domain, every HTTP exchange, the event awaited, the screenshot.
- \+ Stage 2 is untouched; reporting is a property of the assembly, like the environment.
- \+ A flaky test does not break the pipeline and does not disappear: summary, warning, Retries tab, trend.
- − Pointcuts name methods by string: a rename silently drops a step (a test catches it, the compiler does not).
- − The agent is part of the run: an IDE run without the `argLine` has no steps (`ReportingTest` fails there too).
- − The weaver adds ~1 s to the JVM start and an `--add-opens`.
- − Steps after the test body (cleanup) are in the test's report too: useful when cleanup fails, noise otherwise.
- − A rerun writes its screenshot over the first attempt's in `target/artifacts`; Allure keeps both.
- − The pipeline was written against a repository without a remote: its commands were run locally, the workflow
  itself is unverified until the first push.
