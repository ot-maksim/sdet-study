# ADR 0010 — Events: the SUT publishes to Kafka, tests read with one EventClient per run

Status: accepted (stage 4b).

## Context

Stage 1 taught the thread-safety of an event client on a fake broker (`principles/concurrency/events/kafka`) and
left the Kafka client itself in a note (`docs/stage4/kafka-event-client.md`), never run against a broker. The
template needs a fourth channel next to API, DB and page: "what the API returned is what the service announced".
The SUT had no events, and the compose stand and stage 2 must keep working without a broker.

## Decision

- **SUT**: a port `users.UserEvents` called by `UserService.create` after the insert; adapters `UserEvents.none()`
  (default) and `events.KafkaUserEvents`, chosen by `SUT_KAFKA_BOOTSTRAP` in `SutMain`. Topic `users`, 3 partitions,
  created by the SUT on start (as `schema.sql`); key = user id, value = user JSON, header `event-type`.
  **Fire-and-forget**: async `send`, failure logged, the `201` does not wait for the broker.
- **Client**: `com.study.template.events` — `EventClient` from the note, `Event`, `EventSubscription`,
  `SwitchableListener`, `EventWaiter` from the kata. No Spring imports: it could move to a `framework/events` layer
  unchanged. One client per run (singleton bean, one consumer thread, closed with the context).
- **Test side of the topic**: `events.users.UserEvents`, `@TestScoped` — subscribed while the test's parameters are
  resolved (before the body), unsubscribed when the test ends; finds events by user id, keeps the ones it skipped.
- **Environment**: `SutEnvironment.kafkaBootstrap` (optional). `ContainersEnvironment` always starts
  `apache/kafka-native` (~1 s, in parallel with Postgres) with a listener for the private network; the SUT gets
  `kafka:19092`, tests the mapped port. `StandEnvironment` reads `template.stand.kafka-bootstrap` (empty by default).
- **Capability, not environment**: `EnvironmentProvider.eventsUnavailable()` is asked only for `@EventTests`
  (tag `events`). A stand without Kafka skips those (fails under `ci`) and runs the rest.
- **Compose**: a `kafka` service under the `events` profile; the SUT's variable defaults to empty.
- Auto-creation of topics is off on every broker and in the client: a typo in a topic name is an error.

## Found by the real broker (not by the kata's fake)

1. A **second listener on a topic already assigned** got events sent before its `subscribe()` returned: the
   consumer's position is shared, and for an assigned topic it may still be behind records not yet polled. Fix:
   every listener has its own start = end offsets at subscribe time; registration moved onto the poll thread.
2. A **topic created a moment ago** was "not found" now and then: `createTopics()` completes on the controller, the
   broker's metadata catches up a moment later. Fix: a 3 s grace in `partitionsFor` before failing.
3. `EventWaiter.pollMatching` **drops** what does not match — right for one expectation, wrong for a test that waits
   for two users in another order. `UserEvents` keeps what it skipped.

## Alternatives

| Option | Why not |
|---|---|
| Consumer group + `subscribe()` per test | Rebalances on every join, committed offsets shared between runs, seconds per test |
| A consumer per test | One more thread and connection per test, and the same race on its first poll; one per run is enough with per-listener starts |
| Spring Kafka (`@KafkaListener`, `KafkaTemplate`) | Listeners are beans for the context's lifetime, not per test; the client's design is the study object |
| Synchronous send in the SUT (`send().get()`) | Hides the asynchrony tests must handle; a broker outage would fail user creation (dual write either way) |
| Outbox table in the SUT | The right production answer to the dual write; a lot of SUT code that teaches nothing about the framework |
| Kafka always in compose | Every stage 2 run would pay for a broker it does not use |
| Broker per test class in E2E (`@Container`) | ADR 0009: one environment per run. The client's own test does own its broker — a component test |

## Consequences

- \+ The same `mvn verify` covers four channels; the event tests run in parallel with the rest, among other tests' events.
- \+ A stand without Kafka loses only the event tests, with a reason in the report.
- \+ The SUT and stage 2 are unchanged without the variable (the second `UserService` constructor keeps the old one).
- − Waiting for an event is bounded by `template.events.timeout`, not instant: a missing event costs 10 s.
- − The client's poll thread runs listeners: a slow listener delays every subscription of the run.
- − Compose with events needs two things: the profile and `SUT_KAFKA_BOOTSTRAP` (a profile cannot change env).
- − "No event was published" cannot be checked without waiting; the template checks only what arrives.
