# sdet-study

ООП и архитектура ПО на практике, на примерах из автоматизации тестирования. План и принятые решения — в [docs/PLAN.md](docs/PLAN.md).

| Модуль | Что внутри |
|---|---|
| `basics/` | Порядок инициализации классов и объектов (`com.study.init`), паттерн Builder (`com.study.patterns.builder`) |
| `principles/` | Каталы по принципам: `com.study.principles.<topic>.{bad,good}`, README в пакете темы |

Требования: Java 17, Maven 3.9+.

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
