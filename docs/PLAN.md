# План: ООП и архитектура ПО на практике (SDET, Java)

## Цель

Систематизировать принципы ООП и архитектуры (инкапсуляция/абстракция, coupling/cohesion,
наследование vs композиция, DI, SOLID, KISS/DRY/DAMP/YAGNI) и закрепить их на примерах
из автоматизации, а затем применить в учебном скелете тестового фреймворка
(UI Web/Mobile — Playwright, REST — RestAssured, DB — JDBC).

## Принятые решения

| # | Вопрос | Решение |
|---|--------|---------|
| 1 | Формат | Гибрид: каталы по принципам (`principles/`) + скелет фреймворка (`framework/`), всё в `main` |
| 2 | SUT | Свой, в репозитории (`sut/`) |
| 3 | Уровень фреймворка | **Учебный скелет**: каждый слой минимален, но архитектурно правильный. Рабочий шаблон — отдельный этап позже |
| 4 | DI | **Вручную** (Composition Root в JUnit 5 extension). Spring / Spring Boot — на этапе рабочего шаблона |
| 5 | Порядок | Сначала систематизация (каталы), потом применение во фреймворке |
| 6 | БД | Postgres в Docker. SUT + Postgres поднимаются через `docker compose`; Testcontainers — на этапе рабочего шаблона (см. ниже) |
| 7 | Mobile | Mobile web через Playwright device emulation; native mobile — интерфейс-заглушка под Appium |

## Структура репозитория

```
sdet-study/                  parent pom, Java 17, JUnit 5, AssertJ, jqwik
├── basics/                  существующие init/ и patterns/builder
├── principles/              каталы: com.study.principles.<topic>.{bad,good} + README.md
├── sut/                     тестируемое приложение: Javalin (REST + HTML) + Postgres (JDBC)
├── framework/               учебный скелет тестового фреймворка
│   ├── core/                config, lifecycle, composition root, test data
│   ├── api/                 RestAssured-клиенты
│   ├── ui/                  Playwright: pages + components, web/mobile профили
│   ├── db/                  JDBC-репозитории
│   ├── domain/              бизнес-шаги (facade над api/ui/db)
│   └── tests/               E2E и интеграционные тесты
├── docker-compose.yml       postgres + sut
└── docs/                    план, ADR-заметки
```

## Нарезка сессий

Одна сессия Claude Code на блок; контекст между сессиями передаётся через этот файл, `CLAUDE.md` и README каталов.

| Сессия | Содержание |
|---|---|
| 0 | План, структура, multi-module Maven ✅ |
| 1a | Инкапсуляция/абстракция, deep vs shallow, coupling + DI, закон Деметры |
| 1b | Наследование vs композиция, SRP, OCP, LSP, ISP |
| 1c | KISS / DRY / DAMP / YAGNI + сводная шпаргалка «как распознать запах» |
| 2 | SUT + учебный скелет фреймворка (при разрастании — делим на SUT и слои) |
| 3 | Ревью намеренно плохого фреймворка |
| 4 | Рабочий шаблон (Spring, Testcontainers, Allure, CI) |

## Этапы

### Этап 0. Инфраструктура
- [x] Multi-module Maven: `basics/` (init, builder), `principles/` (каталы)
- [x] JUnit 5, AssertJ, jqwik — версии в `dependencyManagement` родителя

### Этап 1. Систематизация: каталы (`principles/`)
Шаблон катала: README (суть, плюсы/минусы, когда НЕ применять) → `bad` → `good` →
тест, показывающий разницу → упражнение «попробуй сам». Без внешних систем, на fakes.

- [x] Инварианты класса (`principles/invariants`: hashkey, leak, record, testrun, retry + property-based тесты)
- [x] Инкапсуляция / абстракция — мутабельный `TestConfig` vs неизменяемый с инвариантами — `principles/encapsulation`
- [x] Records в автоматизации — где подходит (DTO, тест-данные), ловушки (`toString`, массивы), где нет (клиенты, page objects) — `principles/records`
- [x] Даты и время в тестах — внедрённый `Clock` vs `now()`, выбор типа (`Instant`/`LocalDate`/`ZonedDateTime`/`Duration` vs `Period`, DST), ловушки форматов (`YYYY`, `hh`, `SimpleDateFormat`), проверка серверных timestamp'ов (окно, допуск, точность) — `principles/time`
- [x] Параллельный запуск и общее состояние — общий `RequestSpecification` / `static WebDriver` vs неизменяемый клиент + драйвер на тест; что делить и какой структурой (Store compute-once, `AtomicLong`, `ConcurrentLinkedQueue`, `BlockingQueue`-пул, `@ResourceLock`, изоляция данными); каталог из 9 механизмов дизайна; единица параллелизации (JUnit / TestNG / Cucumber); JUnit 5 vs TestNG в коде (экземпляр на класс, `ITestResult`-скоуп, `timeOut`-поток, `@Factory`, свой лок через `IHookable`); ловушки `ThreadLocal` — `principles/parallel`
- [ ] Конкурентность в тестах — когда тесту нужен больше чем один поток; обзор сценариев и инструментов — `principles/concurrency`
  - [x] а. Перенос `async` → `concurrency/events/kafka`, общее ядро `events` (`EventWaiter`, `SwitchableListener`)
  - [x] б. `events/websocket` — push без replay на JDK `WebSocket`: готовность = ack сервера, demand (`request(1)`), фрагменты (`last`), потеря соединения как быстрая ошибка с причиной (pill в очереди), reconnect-разрыв → проверка состояния через API (фейк-сервер)
  - [x] в. `events/webhook` — тест как сервер на JDK `HttpServer`: порт 0 и адрес, достижимый для SUT; запись → мгновенный 2xx → проверки в потоке теста; ассерт в хэндлере теряется; at-least-once: события vs доставки, `respondNext` для проверки retry-политики SUT; HMAC по сырому телу
  - [x] г. `events/ui` — фейковая страница в форме API Playwright: обработчик диалога до клика (auto-dismiss), «run and wait» (`waitForResponse(predicate, action)`) против ожидания после скриншота шага, снапшоты vs web-first ассерты, транзиентный toast: поллинг 500 мс vs ассерт сразу vs `page.clock()`
  - [x] д. `race` — гонки в SUT (racy vs safe) × обвязка (bad vs good): общий пул решает конкурентность, `CyclicBarrier` больше пула = вечное ожидание, per-call ассерты не видят межвызовный инвариант; `StartingGate` (свой пул N, ready/start, все исходы против одного дедлайна), измерение перекрытия; двойное списание, lost update, idempotency key, «доказательство vs свидетельство»
  - [x] е. `eventual` — eventual consistency: ассерт сразу / `sleep` / поллинг без дедлайна с `catch (Exception)` / ожидание точного промежуточного статуса; `Poll` (дедлайн, только 404 = «ещё нет», возврат значения, сообщение с последним значением/ошибкой, поток вызывающего); Awaitility: `ignoreException` vs `ignoreExceptions()`, свой поток vs `pollInSameThread()`, `during` для отсутствия; Awaitility 4.2.2 — test-зависимость
  - [ ] ж. `fanout` + `futures` — параллельная подготовка данных; SUT, возвращающий `CompletableFuture`; потеря MDC/`ThreadLocal`, утечка потоков
  - Kafka-часть (было `principles/async`): слушатель в чужом потоке: `ArrayList` vs `BlockingQueue`, `sleep` vs `poll(timeout)` vs `pollMatching` против шума, реестр слушателей на `CopyOnWriteArrayList` и запоздалый вызов после отписки, `SwitchableListener` (`volatile` vs `synchronized`), отписка того же экземпляра, упавший слушатель, `subscribe` = готовность; фейковый брокер вместо Kafka
- [ ] Deep vs Shallow — `HttpHelper` vs `UserApi.create(user)`; temporal coupling
- [ ] Coupling + DI — page object с `new Browser()` vs внедрение; DIP / IoC / DI
- [ ] Закон Деметры — цепочки навигации vs намеренные действия; когда fluent API — не нарушение
- [ ] Наследование vs композиция — `BaseTest`-god-class, взрыв иерархии страниц vs компоненты/extensions
  - [x] Абстрактный класс как владелец состояния с инвариантом: жизненный цикл сессии драйвера (`inheritance.session`: bad / good / composition)
- [ ] SRP — page object «всё в одном» → разделение по причинам изменения
- [ ] OCP — `switch(browser/env)` → Strategy/registry
- [ ] LSP — `ReadOnlyDbClient extends DbClient` → корректная иерархия
- [ ] ISP — толстый `Driver` → ролевые интерфейсы
- [x] DIP — тест знает про SQL → `UserPrecondition` (API/DB реализации) — `principles/dip`
- [ ] KISS / DRY / DAMP / YAGNI — шаг с boolean-флагами, пересушенные тесты

### Этап 2. Применение: SUT + учебный скелет фреймворка
- [ ] `sut/`: Javalin, REST `/users`, HTML-страница со списком, Postgres через JDBC, `docker compose`
- [ ] `framework/core`: конфиг, Composition Root вручную (JUnit 5 extension)
- [ ] `framework/api`: RestAssured-клиент (deep interface, DTO + builder)
- [ ] `framework/db`: JDBC-репозиторий
- [ ] `framework/ui`: Playwright, Page Object + Components, web/mobile-web профили, заглушка native
- [ ] `framework/domain`: бизнес-шаги (Facade), предусловия через разные каналы (DIP)
- [ ] `framework/tests`: E2E «API → DB → UI», интеграционные тесты по слоям
- [ ] ADR-заметка к каждому ключевому решению: принцип, альтернативы, компромисс

### Этап 3. Ревью архитектуры
- [ ] Намеренно плохой мини-фреймворк с 8–10 заложенными проблемами → ревью → сверка → рефакторинг

### Этап 4 (позже). Рабочий шаблон фреймворка
- [ ] Spring / Spring Boot test context вместо ручного DI — сравнение с этапом 2
- [ ] Testcontainers как альтернативный `EnvironmentProvider` (герметичный прогон)
- [ ] Kafka `EventClient` на настоящем брокере (Testcontainers) + интеграционный тест — заготовка в [stage4/kafka-event-client.md](stage4/kafka-event-client.md), дизайн разобран в `principles/concurrency/events/kafka`
- [ ] Профили окружений, Allure (listener → Observer), параллельный запуск, retry, CI
- [ ] Appium-реализация native mobile за уже существующим интерфейсом

## Почему Testcontainers не на этапе 2

Фреймворк для E2E в реальности **не владеет** окружением: SUT и его БД уже развёрнуты,
фреймворк лишь подключается по URL / JDBC URL из конфига. `docker compose up` моделирует
именно это и держит учебный скелет простым (KISS).

Testcontainers полезен, когда тесты должны **сами** управлять окружением: герметичный прогон
в CI без заранее поднятого стенда, чистая БД на каждый прогон/класс, изоляция при параллельном
запуске, интеграционные тесты компонентов. Это задачи рабочего шаблона (этап 4). Архитектурно
он подключится без изменений тестов — как ещё одна реализация «откуда взять окружение»
(DIP + OCP), что само по себе хороший пример.
