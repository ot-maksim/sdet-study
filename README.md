# sdet-study

ООП и архитектура ПО на практике, на примерах из автоматизации тестирования. План и принятые решения — в [docs/PLAN.md](docs/PLAN.md).

| Модуль | Что внутри |
|---|---|
| `basics/` | Порядок инициализации классов и объектов (`com.study.init`), паттерн Builder (`com.study.patterns.builder`) |
| `principles/` | Каталы по принципам: `com.study.principles.<topic>.{bad,good}`, README в пакете темы |
| `framework/` | Учебный скелет фреймворка: `core` (конфиг, жизненный цикл, механика composition root) — [framework/core/README.md](framework/core/README.md), `api` (RestAssured-клиенты) — [framework/api/README.md](framework/api/README.md), `db` (JDBC: проверки и seeding) — [framework/db/README.md](framework/db/README.md), `ui` (Playwright, параллельные UI-тесты) — [framework/ui/README.md](framework/ui/README.md), `domain` (бизнес-шаги, предусловия API/DB) — [framework/domain/README.md](framework/domain/README.md), `tests` (composition root, API/UI/E2E-тесты) — [framework/tests/README.md](framework/tests/README.md); решения — [docs/adr](docs/adr) |
| `sut/` | Тестируемое приложение: пользователи, REST + HTML на Javalin, Postgres через JDBC — [sut/README.md](sut/README.md) |

Требования: Java 17, Maven 3.9+; для стенда — Docker; для UI — Chromium от Playwright (см. framework/ui/README.md).

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
