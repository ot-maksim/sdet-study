# Dates and time in tests: control the clock, pick the type, format exactly, assert moments

Time is a **hidden input**. Code that calls `now()` reads a global variable that changes on every run and
differs between machines; types that drop the zone (or invent one) are right only part of the year.
In tests that means flakiness at midnight, on the DST weekend, at the end of December, on CI in UTC.
Four rules: **inject the clock**, **one type per concept**, **unambiguous formats**, **assert instants,
not strings**.

| Package | What to look at |
|---|---|
| root | `MutableClock` — test clock you move with `advance(..)`; `withZone` shares the time. `TimeDemo` runs every part |
| `clock/bad` | `AuthTokenCache` with `Instant.now()`: refresh testable only by sleeping. `TestUsers` with `LocalDate.now()`: data depends on the runner's default zone |
| `clock/good` | the same with an injected `Clock`: refresh boundary checked to the millisecond; test data follows the SUT's business zone |
| `clock` | `AccessToken`, `TokenIssuer`, `FakeTokenIssuer`, `AgeRule` (the SUT rule takes `today` as a parameter) |
| `types/bad` | `MeetingRequests`: "tomorrow 09:00 Berlin" via `LocalDateTime` + `ZoneOffset.ofHours(1)` — wrong in summer and after midnight |
| `types/good` | `ZoneId` + calendar arithmetic on `LocalDate` + `atZone(..).toInstant()` |
| `format/bad` | `DateFormats`: `YYYY`, `hh`, shared lenient `SimpleDateFormat`, default locale |
| `format/good` | `ISO_LOCAL_DATE`, `HH`, `uuuu` + `STRICT`, explicit locale |
| `assertions` | `FakeOrdersApi` (own clock and zone, millisecond precision, two serializers), `OrderResponse` |
| `assertions/{bad,good}` (under `src/test`) | assertion helpers: string/`now()` comparisons vs windows, tolerances and `Duration`s. They live in test sources because they are test code and AssertJ is test-scoped |

Run:
```
mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.time.TimeDemo
mvn -q -pl principles test -Dtest='com.study.principles.time.**.*Test'
```

- `TokenCacheTest` — bad: shrink the TTL and `Thread.sleep`; good: 1 ms before and exactly at the boundary, no waiting.
- `TestUsersTest` — the bad factory gives different data in UTC+14 and UTC-11; the good one does not care.
- `AgeBoundaryPropertyTest` (jqwik) — for *any* today (leap days included) the boundary users fall on the right side.
- `MeetingRequestsTest` — winter, summer, the DST weekend, 23:30 UTC; bad is right only in the first case.
- `TimeTypesFactsTest` — executable facts behind the type table below.
- `DateFormatsTest` — every formatting trap as a passing fact, and the strict good formats.
- `OrderAssertionsTest` — every response is correct; bad helpers reject some, good ones accept all and still catch a real bug.

## 1. Clock: time is a dependency

| Situation | What to do |
|---|---|
| Your framework code decides by time (token refresh, retry deadline, cache TTL, report "for yesterday") | Constructor-inject `java.time.Clock`; `Clock.systemUTC()` in the composition root |
| Unit test reads time once | `Clock.fixed(instant, zone)` |
| Test must let time pass | `MutableClock.advance(..)` instead of `Thread.sleep` |
| Test data relative to "today" | `LocalDate.now(sutClock)` where the clock is in the **SUT's business zone** from config |
| The SUT has a test-clock endpoint / feature flag | Drive it from the test: the only way to test "trial expires in 30 days" end to end |
| Black-box E2E against a deployed SUT | You do not own its clock: assert relative to it (windows, tolerances, see §4), never against exact values |
| A pure rule (`isAdult(birth, today)`) | Pass `today` as a parameter: no clock at all is the simplest seam |

`LocalDate.now()` without arguments = `Clock.systemDefaultZone()` = the **runner's** zone. CI agents run
in UTC, laptops in Europe/Moscow. That's three hours a day when the date differs.

## 2. Types: one concept, one type

| Type | Means | Use for | Do not use for |
|---|---|---|---|
| `Instant` | a point on the global timeline | event timestamps, `createdAt`, expiry, logs, comparing API/DB moments | anything a human reads as a calendar (no months, no days of week) |
| `LocalDate` | a calendar date, no zone | birthday, business date, report period, holiday | a moment: "today" differs across zones |
| `LocalTime` | wall-clock time | opening hours, "every day at 09:00" | |
| `LocalDateTime` | date + wall-clock time, **no zone** | rarely: input to `atZone(..)`, a UI field before you know the zone | event timestamps (ambiguous in the fall-back hour, see the facts test) |
| `ZonedDateTime` | local date-time + `ZoneId` with DST rules | scheduling for people ("09:00 Berlin tomorrow"), calendar arithmetic across DST | storage and equality (zone rules change; compare as `Instant`) |
| `OffsetDateTime` | local date-time + fixed offset | the wire: ISO-8601 with offset in JSON, JDBC `timestamptz` | computing future local times (an offset does not know DST) |
| `Duration` | exact amount of seconds | TTLs, timeouts, "30 minutes after creation" | "one month later", "next day at the same time" |
| `Period` | calendar amount (years, months, days) | age, subscription length, "one month later" | timeouts; `Instant.plus(Period.ofMonths(1))` throws |
| `java.util.Date`, `Calendar`, `long` ms | legacy | only at the boundary of an old API; convert immediately | anything in new code |

Facts worth remembering (all in `TimeTypesFactsTest`): a day is 23 or 25 hours twice a year;
`plusDays(1)` ≠ `plus(Duration.ofDays(1))` on a `ZonedDateTime`; `Jan 31 + 1 month = Feb 28` and back is
`Jan 28`; `02:30` on the spring-forward day does not exist and `atZone` silently moves it to `03:30`.

## 3. Formatting and parsing

| Trap | Effect | Fix |
|---|---|---|
| `YYYY` (week-based year) | `2026-12-28` → `2027-12-28` in US locale; `2027-01-01` → `2026-01-01` in German | `uuuu`/`yyyy`, or better, `ISO_LOCAL_DATE` |
| `hh` (12-hour) | `13:05` → `01:05`; passes in the morning | `HH` |
| `mm` vs `MM`, `DD` (day-of-year) vs `dd` | minutes in place of months; `DD` is 313 in November | read the pattern, use ISO constants for machine formats |
| `SimpleDateFormat` | lenient (`30.02` → 2 March), not thread-safe (static + parallel tests = wrong dates) | `DateTimeFormatter` (immutable) with `ResolverStyle.STRICT` |
| STRICT with `yyyy` | parse fails (year-of-era needs an era) | `uuuu` in strict patterns |
| default locale | `November` on CI, `ноября` on a Russian laptop | `Locale.ROOT` for numbers, the UI locale from config for words |
| `Date` ↔ `LocalDate` via `ZoneId.systemDefault()` | date shifts by a day for runners in another zone | stay in `java.time`; convert legacy types at the edge with an explicit zone |

## 4. Asserting server timestamps

| Who owns the clock | Assertion |
|---|---|
| The test (unit, fake, `Clock` injected into the SUT) | exact: `isEqualTo(expectedInstant)` |
| Same machine/clock, real time (in-process SUT, the DB you just wrote) | window: `before ≤ t ≤ after`, `before` truncated to the server's precision |
| Remote server with its own clock | tolerance: `isCloseTo(Instant.now(), within(skew + latency))` |
| Two timestamps from the same server | exact relation: `Duration.between(createdAt, expiresAt) == ttl` |

- **Parse to `Instant` at the boundary.** `Z`, `+00:00`, `+01:00`, `.000` or no fraction: all serializer
  choices; the test asserts moments.
- **Precision.** `Instant.now()` has microseconds (JDK 15+ on Linux/macOS), Postgres keeps microseconds,
  JavaScript and many APIs keep milliseconds, MySQL `DATETIME` keeps seconds by default (and rounds).
  Truncate the expectation, not the actual value.
- **`equals` includes the offset/zone.** AssertJ compares a single `OffsetDateTime`/`ZonedDateTime` by
  instant, but a DTO/record, a `List`, a `Map` key or JUnit `assertEquals` uses `equals`. For DTOs: store
  `Instant`, or `usingRecursiveComparison().withComparatorForType(OffsetDateTime.timeLineOrder(), OffsetDateTime.class)`.
- **Never `Thread.sleep` to "make time pass"** in your own code's tests; move the clock.

## Pros / cons

| Pros | Cons |
|---|---|
| Deterministic tests: fixed clock, no midnight/DST/December surprises | One more constructor parameter in every time-aware class |
| Boundaries testable exactly (1 ms before / at) | `MutableClock` is test infrastructure you must maintain |
| Failures reproducible: replay the exact instant | A fixed clock hides real-time bugs (timeouts, slow responses); keep a few real-clock tests |
| Correct types make wrong code fail to compile (`Instant.plus(Period)`) | More types to know; the right one depends on the business meaning, not on taste |
| ISO formats and instants survive moving the SUT between zones | Tolerance-based asserts can hide small off-by-seconds bugs if the tolerance is too wide |

## When NOT to apply
- **Don't inject a clock into what doesn't decide by time.** A logger timestamp or a report's "generated at"
  label can call `Instant.now()`; a `Clock` parameter there is noise.
- **Don't fake the SUT's time in black-box E2E** by mocking anything inside it. If the SUT has no test-clock
  hook, assert relative to the SUT's clock (§4), or test the time rule at a lower level.
- **Don't freeze time in performance or timeout tests.** They are about real time.
- **Don't build `ZonedDateTime` for purely machine data.** If nobody reads it as a calendar, `Instant` is enough.
- **Don't add a `MutableClock` when `Clock.fixed` works.** Move time only if the test needs time to pass.
- **Don't change `TimeZone.setDefault` / `Locale.setDefault` to fix a test.** It's global state (breaks
  parallel tests); `TestUsersTest` does it only to *show* the dependency.

## Try it yourself
1. Add `trialEndsOn(LocalDate signUp)` = 30 days later to a `Subscription` class and test "on the last
   day the trial is active, the next day it is not" with `MutableClock`. Then change the rule to
   "one month" (`Period.ofMonths(1)`) and run it from 31 January.
2. Give `bad.AuthTokenCache` a package-private constructor taking a `Clock` and make the public one
   delegate with `Clock.systemUTC()`. Is that enough? Who still bypasses it? (Hint: `FakeTokenIssuer`.)
3. Make `FakeOrdersApi` store **seconds** (MySQL `DATETIME`) with **rounding**, not truncation. Which good
   assertion breaks, and what is the right fix: truncate `before`, or widen the window by 1 s on both sides?
4. Write `assertThat(dto).usingRecursiveComparison()...` that makes `bad.OrderAssertions.OrderTimes`
   compare by instant, and decide whether that is better than changing the fields to `Instant`.
5. Smell check on your framework: `grep -rn 'now()\|new Date()\|currentTimeMillis\|SimpleDateFormat\|YYYY\|hh:mm\|Thread.sleep\|ZoneOffset.of'`.
   For each hit decide: inject a clock, change the type, fix the pattern, or leave it (logging).
