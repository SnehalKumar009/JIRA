# ADR-0022: Generated jOOQ code is committed

- **Status:** Accepted
- **Date:** 2026-11-06
- **Refines:** ADR-0005

## Context
jOOQ generates type-safe classes from the database schema, which needs a migrated PostgreSQL at
generation time. The app image is built inside Docker with no Docker of its own, so it can't start a
database for code generation. The Testcontainers jOOQ plugin is unmaintained (2024, Flyway 9).

## Decision
- Generated classes live in `backend/src/main/jooq` (package `dev.tracker.jooq`) and are committed.
- `scripts/generate-jooq.sh` regenerates them: throwaway PostgreSQL → Flyway migrations → jOOQ
  codegen (Maven profile `jooq-codegen`). Run it after adding a migration.
- CI regenerates and fails if the result differs from what is committed. Generation is
  deterministic (checked).
- Spring Session and Flyway tables are excluded.
- Module ownership of tables is enforced by `TableOwnershipTests`: only the owning module may use a
  table's classes.

## Alternatives considered
- **Generate at build time with Testcontainers** — breaks the Docker image build.
- **jOOQ's DDL parser (no database)** — doesn't understand all PostgreSQL DDL we use (triggers,
  plpgsql).

## Consequences
- Builds need no database. Migrations and generated code are reviewed together.
- Forgetting to regenerate is caught by CI, not at runtime.
