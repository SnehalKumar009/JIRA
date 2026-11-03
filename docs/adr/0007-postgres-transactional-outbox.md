# ADR-0007: Transactional outbox in Postgres, no message broker

- **Status:** Accepted
- **Date:** 2026-10-28

## Context
Side effects (notifications, webhooks, background jobs) must never be lost or sent for changes that rolled back, including across frequent stack restarts (DOMAIN_MODEL invariant 10).

## Decision
- Domain changes insert an `outbox_event` row in the same transaction.
- The worker module (ADR-0001) polls with `SELECT … FOR UPDATE SKIP LOCKED`, processes, marks processed; retries with backoff and an attempt limit; failed events stay visible for inspection.
- Consumers are idempotent (event ID as idempotency key).

## Alternatives considered
- **Kafka / RabbitMQ** — the right tools at scale, but an extra stateful service and memory cost with no benefit for 10 users. Dual-write without an outbox would still be unsafe.
- **Spring Modulith event publication registry** — useful for in-process listeners, but we want an explicit table that also backs webhook delivery logs.

## Consequences
- Exactly-once *recording*, at-least-once *delivery*.
- A relay to a broker can be added later without touching domain code.
- Processed rows need periodic cleanup (retention job).
