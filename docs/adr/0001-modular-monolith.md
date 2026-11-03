# ADR-0001: Modular monolith with an in-process worker

- **Status:** Accepted
- **Date:** 2026-10-28

## Context
The domain requires an issue change, its change history, and its outbox event to commit in one transaction (DOMAIN_MODEL §5, invariants 5 and 10). Scale is 10 users on one desktop with a 3 GB memory budget. Jira itself is a monolith with a plugin system.

## Decision
- One deployable application and one database, split into modules mirroring the domain: `identity`, `project`, `access`, `config`, `issue`, `search`, `board`, `audit`, `platform`.
- Modules talk through public APIs and domain events only; internals are package-private. Boundaries are verified by tests (ADR-0014).
- Background work (outbox processing, rank rebalancing, jobs) lives in a `worker` module with its own thread pool, enabled by configuration. In P0 it runs in the same JVM as the API.

## Alternatives considered
- **Microservices** — would turn single-transaction invariants into distributed transactions; operational cost with no scale benefit.
- **Separate worker container from day one** — cleaner isolation, but a second JVM costs ~500 MB of the 3 GB budget (ADR-0016).

## Consequences
- Simple transactions and deployment; one thing to run, debug, and back up.
- Discipline needed to keep module boundaries clean — enforced in CI, not by convention.
- Splitting the worker out later is a configuration change.
