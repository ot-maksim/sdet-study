# sdet-study

ООП и архитектура ПО на практике, на примерах из автоматизации тестирования. План и принятые решения — в [docs/PLAN.md](docs/PLAN.md).

| Модуль | Что внутри |
|---|---|
| `basics/` | Порядок инициализации классов и объектов (`com.study.init`), паттерн Builder (`com.study.patterns.builder`) |
| `principles/` | Каталы по принципам: `com.study.principles.<topic>.{bad,good}`, README в пакете темы |
| `framework/` | Учебный скелет фреймворка: `core` (конфиг, жизненный цикл, механика composition root) — [framework/core/README.md](framework/core/README.md), `api` (RestAssured-клиенты) — [framework/api/README.md](framework/api/README.md), `db` (JDBC: проверки и seeding) — [framework/db/README.md](framework/db/README.md), `ui` (Playwright, параллельные UI-тесты) — [framework/ui/README.md](framework/ui/README.md), `domain` (бизнес-шаги, предусловия API/DB) — [framework/domain/README.md](framework/domain/README.md), `tests` (composition root, API/UI/E2E-тесты) — [framework/tests/README.md](framework/tests/README.md); решения — [docs/adr](docs/adr) |
| `review/` | Этап 3: ревью унаследованного фреймворка — `bad` с 10 заложенными проблемами и 3 ложными срабатываниями, `good` с рефакторингом, по тесту `P<NN>*ReviewTest` на проблему; ответы — [review/REVIEW.md](review/REVIEW.md) (сначала попробовать самому). Как проводить ревью — [review/README.md](review/README.md) |
| `template/` | Этап 4: рабочий шаблон. Слои `framework/` переиспользуются без изменений, новая только сборка: Spring Boot test context (`TemplateConfiguration`, `@TestScoped`), окружение `containers` (Testcontainers, по умолчанию) или `stand`, события Kafka, отчёт Allure, правила слоёв на ArchUnit — [template/README.md](template/README.md) |
| `project/` | Этап 5: рабочий проект «как в команде» на том же стеке и SUT — один модуль, слои = пакеты + ArchUnit (ADR 0012), API-клиент генерируется из OpenAPI-контракта SUT, каждый ответ сверяется с контрактом (ADR 0013). `project/junit` — [project/junit/README.md](project/junit/README.md); `project/testng` — тот же проект на TestNG, отличается только glue раннера в `assembly/` (ADR 0014, `project/check-twins.sh`) — [project/testng/README.md](project/testng/README.md) |
| `sut/` | Тестируемое приложение: пользователи, REST + HTML на Javalin, Postgres через JDBC, OpenAPI-контракт `src/main/resources/openapi.yaml` — [sut/README.md](sut/README.md) |

Критерий для `framework/`, `review/`, `template/` и `project/` — свойства хорошего тестового фреймворка и сценарии изменений,
которые их проверяют: [docs/test-framework-architecture.md](docs/test-framework-architecture.md).

Требования: Java 17, Maven 3.9+; для стенда, `template/` и `project/` — Docker; для UI — Chromium от Playwright (см. framework/ui/README.md).

```bash
mvn verify
```

```bash
mvn -q -pl basics exec:java
```

```bash
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.dip.good.DipDemo
```

Тесты одного модуля или одного класса:

```bash
mvn -q -pl principles test -Dtest=GoodTestRunTest
```

Стенд (Postgres + SUT), к которому подключается фреймворк:

```bash
mvn -q -pl sut -am package -DskipTests
```

```bash
docker compose up -d --build --wait
```

Тесты фреймворка против стенда (параллельно; без стенда — пропускаются с причиной):

```bash
mvn -q -pl framework/tests -am test
```

Тесты ревью (SUT поднимается в процессе; P08 без compose-стенда пропускается):

```bash
mvn -q -pl review -am test
```

Шаблон: герметичный прогон, окружение поднимает Testcontainers (нужен только Docker):

```bash
mvn -q -pl template -am verify
```

Тот же шаблон против compose-стенда:

```bash
mvn -q -pl template -am verify -Dspring.profiles.active=stand
```
