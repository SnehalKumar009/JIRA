# Architecture & Tech Stack — Jira-style Issue Tracker

**Status:** Accepted 2026-10-28
**Related:** [FEATURES.md](FEATURES.md) · [DOMAIN_MODEL.md](DOMAIN_MODEL.md) · [MILESTONES.md](MILESTONES.md) · [ADRs](docs/adr/README.md)

This is the summary. Each major choice has its own Architecture Decision Record in [`docs/adr/`](docs/adr/README.md) with the context, the alternatives, and the consequences.

---

## 1. Constraints that shaped the stack

| Constraint | Source | Effect |
|---|---|---|
| Production-grade engineering | FEATURES §2 | Proven components, no hand-rolled security, tests against real infrastructure |
| Desktop scale: 10 users, ~100k issues | Decision 8 | One database, no broker, no cache server, no search engine |
| Docker Compose on the desktop, stopped and started often | Decision 9 | All state in Postgres volumes; clean shutdown; fast, idempotent startup |
| **Whole stack under 3 GB RAM** | Tech stack Q&A | Worker runs in-process; per-service memory limits; lightweight observability |
| Change + history + outbox in one transaction | DOMAIN_MODEL §5 (#5, #10) | Monolith with a single relational database |

## 2. Architecture

**Modular monolith** — one codebase, one database, strict internal modules matching the domain model, with boundaries enforced by tests ([ADR-0001](docs/adr/0001-modular-monolith.md)).

Modules: `identity` · `project` · `access` · `config` · `issue` · `search` · `board` · `audit` · `platform`

```
Browser ──HTTPS :8443──▶ Caddy (TLS, local CA)
                     ├──▶ tracker.test ──▶ app (JVM)
                     │                      ├─ REST API + BFF sessions
                     │                      ├─ serves the frontend
                     │                      └─ worker module (outbox, jobs) — same JVM in P0
                     └──▶ auth.test ────▶ Keycloak
                                              │
                         app ─────────────────┴──▶ PostgreSQL
                                                    ├─ tracker database
                                                    └─ keycloak database

  optional "observability" profile: Prometheus · Jaeger · Grafana
```

The worker is a separate module with its own thread pool, switched on by configuration. Splitting it into its own container later is a config change, not a code change — it runs in-process in P0 only to stay inside the memory budget.

## 3. Stack

| Layer | Choice | ADR |
|---|---|---|
| Identity provider | Keycloak (production mode, Postgres-backed), OIDC Authorization Code + PKCE, BFF sessions | [0002](docs/adr/0002-keycloak-oidc-bff.md) |
| Backend | **Java 21 (LTS) + Spring Boot 4.1**, built with Maven | [0003](docs/adr/0003-java-spring-boot.md), [0017](docs/adr/0017-java-21.md), [0018](docs/adr/0018-maven-and-repository-layout.md) |
| Database | **PostgreSQL** — one instance, separate databases and users for app and Keycloak | [0004](docs/adr/0004-postgresql-single-datastore.md) |
| Migrations / data access | **Flyway** + **jOOQ** (code generated from the migrated schema) | [0005](docs/adr/0005-flyway-jooq.md) |
| Custom-field storage | Typed value table (one row per issue per field, typed columns) | [0006](docs/adr/0006-custom-field-typed-value-table.md) |
| Async work | Transactional outbox in Postgres, polled with `FOR UPDATE SKIP LOCKED` — no message broker | [0007](docs/adr/0007-postgres-transactional-outbox.md) |
| Sessions / caching | Server-side sessions in Postgres (Spring Session JDBC); in-process caches — no Redis | [0008](docs/adr/0008-sessions-and-caching-without-redis.md) |
| JQL | ANTLR4 grammar → AST → jOOQ conditions | [0009](docs/adr/0009-jql-parser-antlr.md) |
| Frontend | React + TypeScript + Vite SPA, same origin as the API; TanStack Query, React Router, dnd-kit, generated OpenAPI client | [0010](docs/adr/0010-react-vite-spa-same-origin.md) |
| Rich text | ProseMirror JSON (Tiptap editor), schema-validated on the server; never HTML | [0011](docs/adr/0011-rich-text-prosemirror-json.md) |
| Edge | Caddy with a local CA; `https://tracker.test:8443` / `https://auth.test:8443` | [0012](docs/adr/0012-caddy-local-tls-hostnames.md), [0019](docs/adr/0019-configurable-https-port.md) |
| Observability | JSON logs to stdout; Micrometer metrics + tracing (OTLP); optional Prometheus / Jaeger / Grafana profile | [0013](docs/adr/0013-observability.md) |
| Testing | JUnit 5, Testcontainers (real Postgres + Keycloak), Spring Modulith boundary tests, Playwright E2E | [0014](docs/adr/0014-testing-strategy.md) |
| Repo / CI / supply chain | GitHub + GitHub Actions; Trivy, gitleaks, Renovate | [0015](docs/adr/0015-github-actions-supply-chain.md) |
| Memory budget | Per-service limits, JVM tuning, in-process worker | [0016](docs/adr/0016-memory-budget.md) |
| Rate limiting | Bucket4j in the app (per user / per token) | — |

## 4. Memory budget (< 3 GB)

Enforced with `mem_limit` on every Compose service. Measuring actual usage with `docker stats` is an M0 exit criterion.

| Service | Limit | Notes |
|---|---|---|
| PostgreSQL | 256 MB | Small `shared_buffers`; ~100k issues fits easily |
| Keycloak | 768 MB | Optimised image (`kc.sh build`), heap capped by percentage of container limit |
| app (API + BFF + worker) | 768 MB | Heap ≈ 60% of limit; Class Data Sharing for faster start |
| Caddy | 64 MB | |
| **Core total** | **≈ 1.9 GB** | |
| Prometheus | 192 MB | Optional profile |
| Jaeger (all-in-one, in-memory, capped) | 192 MB | Optional profile; traces lost on restart by design |
| Grafana | 256 MB | Optional profile |
| **With observability** | **≈ 2.5 GB** | |

Measured in M0: core stack ~800 MiB, with observability ~1.05 GiB — see [ADR-0016](docs/adr/0016-memory-budget.md).

Fallbacks if M0 measurements exceed the budget, in order: tighten JVM heap settings → drop Grafana (use Prometheus / Jaeger UIs) → GraalVM native image for the app.

## 5. Versions

Pinned in M0 (image digests in `compose.yaml` and the Dockerfiles), kept current by Renovate:

Java 21 (LTS, [ADR-0017](docs/adr/0017-java-21.md)) · Spring Boot 4.1 · Spring Modulith 2.1 · springdoc 3.2 · PostgreSQL 18.6 · Keycloak 26.8 · Caddy 2.11 · Node.js 22 (build only) · React 19 · Vite 8 · TypeScript 7 · Prometheus 3.15 · Jaeger 2.22 · Grafana 13

## 6. Deliberately not used

| Not used | Why |
|---|---|
| Microservices | Would break single-transaction invariants; no scale need |
| Kafka / RabbitMQ | Outbox + worker covers it; outbox can publish to a broker later without domain changes |
| Redis | Sessions in Postgres, caches in-process |
| Elasticsearch / OpenSearch for search | Postgres indexes suffice at ~100k issues |
| JPA / Hibernate | Poor fit for dynamic JQL queries and custom-field storage |
| Next.js | Backend is already the BFF; a second server adds nothing |
| OpenTelemetry Java agent, OTel Collector | Micrometer covers it with less memory |

## 7. Deferred

| Item | When | Notes |
|---|---|---|
| Attachment storage | P1 | S3-compatible API; self-hosted options re-evaluated then |
| UI component library | M1 ADR | |
| LexoRank bucket and rebalancing details | M6 ADR | |
| Log aggregation pipeline | Later | Logs are JSON on stdout; can feed any pipeline |
