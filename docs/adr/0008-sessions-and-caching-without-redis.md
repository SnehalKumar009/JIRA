# ADR-0008: Sessions in Postgres and in-process caches, no Redis

- **Status:** Accepted
- **Date:** 2026-10-28

## Context
BFF sessions must survive `docker compose down` / `up`. Permission resolution (D11) needs caching. Memory budget is tight.

## Decision
- Server-side sessions stored in Postgres via Spring Session JDBC; expired sessions cleaned by a scheduled job.
- Permission and configuration caches are in-process (Caffeine), invalidated explicitly on role, membership, or configuration change.
- If more than one app instance is ever run, invalidation is broadcast with Postgres `LISTEN / NOTIFY`.

## Alternatives considered
- **Redis** — standard choice for both, but another stateful service and memory for no benefit at one instance.
- **In-memory sessions** — lost on every restart; fails the "survives stop / start" requirement.

## Consequences
- Logins persist across restarts.
- Cache correctness depends on invalidation being called on every relevant write — covered by tests in M2.
