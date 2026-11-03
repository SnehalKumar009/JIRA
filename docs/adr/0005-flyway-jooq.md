# ADR-0005: Flyway migrations and jOOQ data access

- **Status:** Accepted
- **Date:** 2026-10-28

## Context
JQL compiles into dynamic SQL; custom fields need joins against a typed value table; schema changes must be versioned and safe to apply on every startup.

## Decision
- **Flyway** owns the schema: versioned SQL migrations, applied on startup, idempotent. Migrations are written to be forward-compatible (expand → migrate → contract).
- **jOOQ** for all data access: classes generated from the migrated schema at build time; type-safe dynamic queries.
- No ORM entity mapping; domain objects are mapped explicitly in each module's repository layer.

## Alternatives considered
- **JPA / Hibernate** — awkward for dynamic query composition and EAV-style storage; lazy loading and dirty checking hide SQL we need to control.
- **Plain JDBC templates** — no type safety for dynamic queries.

## Consequences
- SQL is explicit and reviewable; JQL → SQL is a straightforward AST walk producing jOOQ conditions.
- More mapping code than an ORM.
- Build depends on a schema for code generation (generated from migrations against Testcontainers Postgres).
