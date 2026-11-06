# Architecture Decision Records

Each record captures one significant decision: the context, what we chose, what we rejected, and what follows from it.
Records are immutable once accepted — to change a decision, add a new ADR that supersedes the old one and update the old one's status.

| ADR | Title | Status |
|---|---|---|
| [0001](0001-modular-monolith.md) | Modular monolith with an in-process worker | Accepted |
| [0002](0002-keycloak-oidc-bff.md) | Keycloak + OIDC with the BFF session pattern | Accepted |
| [0003](0003-java-spring-boot.md) | Java + Spring Boot for the backend | Accepted (version amended by 0017) |
| [0004](0004-postgresql-single-datastore.md) | PostgreSQL as the single datastore | Accepted |
| [0005](0005-flyway-jooq.md) | Flyway migrations and jOOQ data access | Accepted |
| [0006](0006-custom-field-typed-value-table.md) | Custom-field values in a typed value table | Accepted |
| [0007](0007-postgres-transactional-outbox.md) | Transactional outbox in Postgres, no message broker | Accepted |
| [0008](0008-sessions-and-caching-without-redis.md) | Sessions in Postgres and in-process caches, no Redis | Accepted |
| [0009](0009-jql-parser-antlr.md) | JQL parser built with ANTLR4 | Accepted |
| [0010](0010-react-vite-spa-same-origin.md) | React + TypeScript + Vite SPA served same-origin | Accepted |
| [0011](0011-rich-text-prosemirror-json.md) | Rich text stored as ProseMirror JSON | Accepted |
| [0012](0012-caddy-local-tls-hostnames.md) | Caddy with a local CA and `.test` hostnames | Accepted (port amended by 0019, app-to-Keycloak calls by 0020) |
| [0013](0013-observability.md) | Observability: JSON logs, Micrometer, optional profile | Accepted |
| [0014](0014-testing-strategy.md) | Testing strategy | Accepted |
| [0015](0015-github-actions-supply-chain.md) | GitHub + GitHub Actions, supply-chain scanning | Accepted |
| [0016](0016-memory-budget.md) | Whole stack under 3 GB RAM | Accepted |
| [0017](0017-java-21.md) | Target Java 21 LTS | Accepted |
| [0018](0018-maven-and-repository-layout.md) | Maven and repository layout | Accepted |
| [0019](0019-configurable-https-port.md) | Configurable HTTPS port, default 8443 | Accepted |
| [0020](0020-oidc-backchannel-urls.md) | Internal URLs for the app's own Keycloak calls | Accepted |
| [0021](0021-mantine-and-nonce-csp.md) | Mantine UI and a nonce-based style policy | Accepted |
| [0022](0022-jooq-generated-code-committed.md) | Generated jOOQ code is committed | Accepted |
| [0023](0023-keycloak-config-cli.md) | Keycloak realm as code with keycloak-config-cli | Accepted |
| [0024](0024-personal-access-tokens.md) | Personal access tokens | Accepted |
| [0025](0025-session-lifecycle.md) | Session lifecycle — back-channel logout plus revalidation | Accepted |

## Template

```markdown
# ADR-NNNN: Title

- **Status:** Proposed | Accepted | Superseded by ADR-NNNN
- **Date:** YYYY-MM-DD

## Context
## Decision
## Alternatives considered
## Consequences
```
