# ADR-0014: Testing strategy

- **Status:** Accepted
- **Date:** 2026-10-28

## Context
Definition of Done requires unit, integration, and E2E tests, authorization tests, and invariant tests. Mocked infrastructure hides real bugs (SQL, transactions, OIDC).

## Decision
- **Unit:** JUnit 5 for domain logic (workflow engine, JQL compiler, rank, permission resolution).
- **Integration:** Testcontainers with real PostgreSQL and real Keycloak; no infrastructure mocks.
- **Architecture:** Spring Modulith verification — the build fails on module-boundary violations.
- **E2E:** Playwright against the full Compose stack for main user journeys.
- **Data:** seed script generates the target-scale dataset (100 projects × ~1k issues) for scale checks.
- Invariants in DOMAIN_MODEL §5 each have at least one test.

## Alternatives considered
- **H2 / in-memory DB for tests** — diverges from Postgres behaviour (locking, SQL dialect).

## Consequences
- Slower integration tests; mitigated by container reuse and parallelism.
- High confidence that what passes in CI works in the Compose stack.
