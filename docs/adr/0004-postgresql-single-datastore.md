# ADR-0004: PostgreSQL as the single datastore

- **Status:** Accepted
- **Date:** 2026-10-28

## Context
We need strong transactions, flexible indexing for JQL, a queue for the outbox, and a store for sessions — at desktop scale (~100k issues) within a 3 GB budget.

## Decision
- One PostgreSQL instance with two databases and two roles: `tracker` (app) and `keycloak`.
- Postgres also serves as: outbox queue (`FOR UPDATE SKIP LOCKED`), session store, and search backend (B-tree, partial, and GIN indexes).
- Data lives in a named Docker volume.
- Backups: `pg_dump` per database via a script; restore script tested in CI-style drills.

## Alternatives considered
- **Separate Postgres containers for app and Keycloak** — stronger isolation, extra ~150 MB and a second backup path.
- **Adding a search engine** — unnecessary at this scale.

## Consequences
- One stateful service to back up, tune, and monitor.
- App and Keycloak share a Postgres version; upgrades are coordinated.
