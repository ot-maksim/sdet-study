# ADR 0002 — Layered configuration with a key catalogue

Status: accepted (stage 2).

## Context

The framework connects to a stand it does not own (`docker compose` locally, a shared environment later): URLs,
DB credentials, timeouts, browser profile. The same suite runs on a laptop and in CI; CI overrides values
through environment variables, a developer through `-D`. Typical pain: a typo in a key silently falls back to a
default, one missing value per restart, a password in a log, "which stand did this run hit?".

## Decision

- Layers, highest first: system properties → environment variables (`sut.base-url` ↔ `SUT_BASE_URL`) →
  `env/<env>.properties` (stand, selected by `framework.env`, default `local`) → `framework.properties`.
- Every module ships its own `framework.properties` with its keys and defaults; they are merged (a key declared
  twice fails). Together they are the **catalogue**: an unknown key in an env file fails the run. Required keys
  without a default are declared blank.
- Modules bind their keys into their own immutable settings record (`config.bind(ApiSettings::from)`); core knows
  no module key. Typed reads collect all problems and report them in one `ConfigException`, with where each value
  came from or where it was looked for.
- Secrets are a type (`Secret`, masked `toString`); `describe()` masks secret-looking keys and is logged at the
  start of a run.
- Defaults live in files, never as literals next to a read.

## Alternatives

| Option | Why not |
|---|---|
| One `config.properties` + `System.getProperty` everywhere | Hidden coupling to a global, no typing, no typo detection |
| `switch (env)` in code | Environments differ in values, not behaviour (the `ocp` kata) |
| Owner / Typesafe Config / MicroProfile Config | Fine libraries; here the point is to see the mechanics. Spring's `@ConfigurationProperties` at stage 4 |
| Fail on the first problem | One restart per missing key — slow in CI |

## Consequences

- \+ Typos in files and all missing values surface at the first resolution of `Config`, with origins.
- \+ Adding a module does not touch core (OCP).
- − Environment variables and system properties cannot be checked for typos (they carry unrelated keys).
- − No destructive-test guard yet (the `ocp` kata's `destructive.allowed`): stage 2 has one local stand. Add it as
  a key when a shared stand appears — YAGNI until then.
