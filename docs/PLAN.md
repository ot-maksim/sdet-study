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
├── review/                  этап 3: унаследованный фреймворк с заложенными проблемами (bad) → ревью → рефакторинг (good)
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
- [x] Конкурентность в тестах — когда тесту нужен больше чем один поток; обзор сценариев и инструментов — `principles/concurrency`
  - [x] а. Перенос `async` → `concurrency/events/kafka`, общее ядро `events` (`EventWaiter`, `SwitchableListener`)
  - [x] б. `events/websocket` — push без replay на JDK `WebSocket`: готовность = ack сервера, demand (`request(1)`), фрагменты (`last`), потеря соединения как быстрая ошибка с причиной (pill в очереди), reconnect-разрыв → проверка состояния через API (фейк-сервер)
  - [x] в. `events/webhook` — тест как сервер на JDK `HttpServer`: порт 0 и адрес, достижимый для SUT; запись → мгновенный 2xx → проверки в потоке теста; ассерт в хэндлере теряется; at-least-once: события vs доставки, `respondNext` для проверки retry-политики SUT; HMAC по сырому телу
  - [x] г. `events/ui` — фейковая страница в форме API Playwright: обработчик диалога до клика (auto-dismiss), «run and wait» (`waitForResponse(predicate, action)`) против ожидания после скриншота шага, снапшоты vs web-first ассерты, транзиентный toast: поллинг 500 мс vs ассерт сразу vs `page.clock()`
  - [x] д. `race` — гонки в SUT (racy vs safe) × обвязка (bad vs good): общий пул решает конкурентность, `CyclicBarrier` больше пула = вечное ожидание, per-call ассерты не видят межвызовный инвариант; `StartingGate` (свой пул N, ready/start, все исходы против одного дедлайна), измерение перекрытия; двойное списание, lost update, idempotency key, «доказательство vs свидетельство»
  - [x] е. `eventual` — eventual consistency: ассерт сразу / `sleep` / поллинг без дедлайна с `catch (Exception)` / ожидание точного промежуточного статуса; `Poll` (дедлайн, только 404 = «ещё нет», возврат значения, сообщение с последним значением/ошибкой, поток вызывающего); Awaitility: `ignoreException` vs `ignoreExceptions()`, свой поток vs `pollInSameThread()`, `during` для отсутствия; Awaitility 4.2.2 — test-зависимость
  - [x] ж. `fanout` + `futures` — параллельные предусловия: свой executor vs common pool, перенос `ThreadLocal`/MDC в ветки (`wrap` с восстановлением), cleanup на ветку (поздняя регистрация), общий дедлайн, все ошибки; SUT с `CompletableFuture`: ассерт в `thenAccept` теряется, `join()` без таймаута, `sleep`+`isDone`, инжектированный `Timer` + `ManualTimer`, пул на экземпляр = утечка потоков, `succeedsWithin`
  - Kafka-часть (было `principles/async`): слушатель в чужом потоке: `ArrayList` vs `BlockingQueue`, `sleep` vs `poll(timeout)` vs `pollMatching` против шума, реестр слушателей на `CopyOnWriteArrayList` и запоздалый вызов после отписки, `SwitchableListener` (`volatile` vs `synchronized`), отписка того же экземпляра, упавший слушатель, `subscribe` = готовность; фейковый брокер вместо Kafka
- [x] Deep vs Shallow — `HttpHelper` + pass-through `UserSteps` vs `UserApi.create(NewUser)`; temporal coupling (порядок через тип `AuthenticatedSession`, запрос с нуля на каждый вызов, результат возвращается, а не хранится); «слишком глубоко» → `tryCreate` для негативных тестов; перевод ошибок на границе; jqwik «любое имя переживает round trip» — `principles/deep`
- [x] Coupling + DI — page object с `new Browser()` (два браузера = потерянный логин) / static `DriverManager` (скрытая зависимость, наследство прошлого теста) / captive dependency (singleton steps держит браузер первого теста) vs внедрение через конструктор; фабрики `Pages` и `Browsers` (владеет и закрывает); composition root `UiExtension` (EngineTestKit: браузер на тест, закрыт и при падении); DIP / IoC / DI / Service Locator; виды coupling и способы внедрения — `principles/coupling`
- [x] Закон Деметры — цепочки навигации по компонентам (редизайн V1→V2 ломает каждый сценарий) vs намеренные действия `AppPage.logout()` (меняется один приватный метод); tell, don't ask; не нарушения: builder, DSL (AssertJ / RestAssured), навигация по DTO; перегиб — `OrderView` с геттером на каждый лист; объекты vs структуры данных, `RETURNS_DEEP_STUBS` как сигнал — `principles/demeter`
- [x] Наследование vs композиция — чеклист «это правда IS-A?», симптомы наследования ради переиспользования — `principles/inheritance`
  - [x] Абстрактный класс как владелец состояния с инвариантом: жизненный цикл сессии драйвера (`inheritance.session`: bad / good / composition)
  - [x] `basetest` — `BaseTest`-god-class (API-тест платит за браузер и БД, свой `@BeforeEach setUp()` молча заменяет базовый → NPE, `UiDbBaseTest` копирует половину `DbBaseTest`) vs JUnit 5 extensions по ресурсу + мета-аннотации `@ApiTest`/`@UiTest`/`@DbTest`, кооперация `ScreenshotOnFailure` ↔ `BrowserExtension` без наследования, test interfaces как миксины без состояния
  - [x] `pages` — дерево `PageWithSidebarAndSearch` (refused bequest `SettingsPage.navigateTo()`, `Mobile*Page` на каждый лист, забытый `MobileReportsPage`) vs компоненты `Header`/`Sidebar`/`SearchBox` и одно решение про устройство в `Pages`
- [x] SRP — `CheckoutPage` «всё в одном» (селекторы + API-подготовка + приватная копия правила цены + проверка в БД + отчёт в stdout с картой в открытом виде + тестовые данные): после редизайна разметки падает и API+DB-тест → класс на причину изменения (`CheckoutPage`, `CartPrecondition`, оракул `ExpectedPrice` с jqwik против API, `Orders`, `ReportingBrowser`-декоратор с маскированием, `Cards`); перегиб — нарезка по техническим ролям (locators/waits/actions/verifications), SRP vs cohesion — `principles/srp`
- [x] OCP — один и тот же `switch(browser)` в трёх местах (`remote` добавлен в фабрику, но забыт в таймаутах → молчаливый `default`; viewport падает в рантайме), окружения как switch с проверкой `!prod` (новый `preprod` открыт для разрушающих тестов) → Strategy `BrowserProfile` + реестр (fail fast, список доступных, `ServiceLoader`-плагин из test-исходников без правки main), контракт-тест по всем профилям; окружения — данные (`.properties`, разрушающие тесты opt-in); когда switch/enum — норма, правило трёх — `principles/ocp`
- [x] LSP — `ReadOnlyDbClient extends DbClient`: громкое нарушение (`UnsupportedOperationException` в cleanup после зелёного теста) и тихое (`SilentReadOnlyDbClient`: no-op, данные остаются), `instanceof`-заплатка → `DbReader` / `DbWriter extends DbReader`, `TestDataCleaner(DbWriter)` не принимает реплику на этапе компиляции; контракты как JUnit test interfaces (EngineTestKit считает нарушенные пункты); фейк — тоже подтип: `LenientFakeUserRepository` (нет уникальности, отдаёт хранимый экземпляр) прячет баг «забыли `update`» vs `InMemoryUserRepository` на общем `UserRepositoryContract` — `principles/lsp`
- [x] ISP — толстый `Driver` (13 методов: web + interaction + cookies + жесты + жизненный цикл приложения; web глушит 4, native 5 через `UnsupportedOperationException`; «общий» `LoginPage` падает на native из-за `open(url)`; 13-методный фейк ради одного `screenshot`) → роли с точки зрения клиентов (`Navigation`, `Interaction`, `Screenshots`-лямбда, `Cookies`, `Gestures`, `AppLifecycle`), агрегаты `Browser`/`MobileApp` только для реализаций и root; комбинирование ролей: intersection type vs два параметра vs агрегат; перегиб — интерфейс на метод — `principles/isp`
- [x] DIP — тест знает про SQL → `UserPrecondition` (API/DB реализации) — `principles/dip`
- [x] KISS / DRY / DAMP / YAGNI — шаг с boolean-флагами, пересушенные тесты
  - [x] `dry` — DRY про знание, не про текст; пересушенные тесты (общий мутабельный шаблон `validUser()` → падение чужого теста только в сьюте, универсальный `run(user, 400, "age_too_low", false)`, ожидания из константы приложения = тавтология (сборка с min age 21 зелёная), `assertThat(equals).isTrue()`, один хелпер на два разошедшихся endpoint'а с флагом `company`) vs «мокрые» (путь и JSON в каждом тесте, переименование = правка на тест) vs DAMP: builder с неважными дефолтами, литеральные ожидания, механика в `UserApi`, свой AssertJ `ResponseAssert`; независимость от порядка (random seed); что сушить, что оставлять в тесте — `principles/dry`
  - [x] `kiss` — шаг `placeOrder(customer, guest, promo, pay, savedCard, verify)`: `FlagAudit` прогоняет все 32 комбинации на корректном магазине и сборках с регрессиями (13 OK, 4 BROKEN — гость с сохранённой картой, 7 LIES — `verify` не видит сломанный промокод, 8 DEAD_FLAG — `savedCard`/`verify` без `pay`) vs методы-намерения и типы (`asGuest()` / `as(customer)` → разные корзины, `PaidOrder` / `PendingOrder`, проверка в тесте, cleanup через реестр); YAGNI — `Reporter` + фабрика + composite + config «на будущее», пришедшее дерево шагов не влезает в `report(String)` vs v1 без абстракций → `StepListener`, выделенный из двух реальных потребителей; правило трёх, что не YAGNI (дешёвые швы сейчас) — `principles/kiss`
  - [x] Сводная шпаргалка «как распознать запах» по всем каталам этапа 1 — [`principles/SMELLS.md`](../principles/SMELLS.md): симптом → как заметить (grep / вопрос на ревью) → принцип → что вместо → катал; разделы по месту (тело теста, данные и жизненный цикл, page objects, клиенты и интерфейсы, структура фреймворка, значения и состояние), «не запахи», принципы в напряжении, один проход grep'ами

### Этап 2. Применение: SUT + учебный скелет фреймворка
- [x] `sut/`: Javalin, REST `/api/users`, HTML-страница `/users` (список, форма, удаление; на телефоне — карточки и свёрнутая форма), Postgres через JDBC, `docker compose` (postgres + sut, healthcheck'и); `UserRepositoryContract` для in-memory и JDBC — [`sut/README.md`](../sut/README.md)
- [x] `framework/core`: конфиг слоями (sysprop → env var → `env/<env>.properties` → `framework.properties` модулей; каталог ключей ловит опечатки, все проблемы разом, `Secret`), `Scope`/`Cleanup` (undo в обратном порядке, все ошибки), механика Composition Root (`CompositionRoot` + `Bindings`: RUN/TEST, лениво, защита от `@BeforeAll`, captive dependency, циклов), `Unique` — [`framework/core/README.md`](../framework/core/README.md), ADR [0001](adr/0001-composition-root-by-hand.md), [0002](adr/0002-layered-config.md). Сам root (привязки модулей) — в `framework/tests`
- [x] `framework/api`: транспорт `ApiClient` (неизменяемый spec, без статики RestAssured, недоступный стенд = одно понятное сообщение) + глубокий `UserApi` (`create` / `tryCreate`, «нет» = только `404 user_not_found`, идемпотентный `deleteIfExists`), свой `ApiResponse` с запросом в сообщениях, `ApiResponseAssert`, DTO-records с withers + `NewUsers`; тесты против настоящего SUT in-process — [`framework/api/README.md`](../framework/api/README.md), ADR [0003](adr/0003-api-client.md)
- [x] `framework/db`: `Database` — JDBC-шаблон (соединение на вызов, SQL и SQLState в ошибке, `queryOne` не берёт «первую из многих»), `UserRows` — единственное место с колонками, свой `UserRow` (db не зависит от api); seeding в обход приложения задокументирован; находки: Postgres **округляет** до микросекунд, `char(2)` с пробелами; тесты на стенде, без него — skipped с причиной — [`framework/db/README.md`](../framework/db/README.md), ADR [0004](adr/0004-db-access.md)
- [x] `framework/ui`: Playwright (только Chromium, ставится явно), **параллельные UI-тесты**: `BrowserPool` — эксклюзивная аренда `Playwright + Browser` через `BlockingQueue`, лениво до `ui.max-browsers`; `UiSession` на тест (свой context); профили `desktop`/`mobile` = опции контекста, компоненты реагируют на состояние страницы, а не на устройство; `UsersScreen` + контракт вместо заглушки native; находки: маркер готовности (пустая таблица на телефоне «невидима»), регэкспы Playwright исполняются в JS (`Pattern.quote` не работает) — [`framework/ui/README.md`](../framework/ui/README.md), ADR [0005](adr/0005-ui-layer.md)
- [x] `framework/domain`: `UserSteps` — фасад: предусловия (`existing`, канал выбирает root) отделены от действий под тестом (`registerThroughApi` / `registerThroughUi`), уборка для любого канала; `UserPrecondition` — API / DB (DIP) с общим контрактом, который ловит дрейф скопированной нормализации; `UserChecks` — сверка API ↔ таблица ↔ страница — [`framework/domain/README.md`](../framework/domain/README.md), ADR [0006](adr/0006-domain-layer.md)
- [x] `framework/tests`: `SutComposition` — единственный root (канал предусловий, профиль браузера, реализация экрана — из конфига), `@ApiTests`/`@UiTests`/`@E2eTests` (тег + root), стенд в `env/local.properties`, параллельный прогон по умолчанию, стенд недоступен → skip с причиной или fail (`stand.required`), скриншот упавшего UI-теста; E2E «API → DB → UI» и обратно; проверено: mobile + DB-предусловия без изменения тестов, уборка и при падениях — [`framework/tests/README.md`](../framework/tests/README.md), ADR [0007](adr/0007-test-assembly.md). Интеграционные тесты по слоям — в модулях слоёв
- [x] ADR-заметка к каждому ключевому решению: принцип, альтернативы, компромисс — [`docs/adr`](adr/) 0001–0007

### Этап 3. Ревью архитектуры
- [x] Намеренно плохой мини-фреймворк → ревью → сверка → рефакторинг — [`review/`](../review/README.md), ключ — [`review/REVIEW.md`](../review/REVIEW.md). «Унаследованный» фреймворк на том же стеке против настоящего SUT: 10 проблем — `BaseTest` god class (API-тесты не стартуют без БД), статика `RestAssured.baseURI`/`Driver`/`db` (два параллельных класса — оба зелёные, оба пользователя на чужом стенде), конфиг-синглтон (ключ в файле ≠ ключ в коде → молча 30 с, `-D` после первого вызова игнорируется, пароль в логе), email из 1000 значений, уборка по префиксу (удаляет пользователя параллельного теста), сырой `Response` (NPE через два шага без 400), page object с ассертами и `sleep` (`page.content()` «находит» несозданного пользователя), SQL конкатенацией (`o'brien@…`), статический `ScenarioContext`, оракул = `UserRules` самого SUT (тавтология; архитектурный тест на импорты) — и 3 ловушки (`switch` в одном месте, fluent-цепочки, `catch (Exception)` с suppressed); тест `P01…P10` на каждую (bad видно, good проходит), good — компактный рефакторинг с `GoodRoot`, порядок рефакторинга в команде

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
