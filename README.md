# Tracker

A Jira-style issue tracker, built as a **production-grade POC**: narrow scope, full production
quality. The goal is to reverse-engineer how Jira works — especially its configuration engine
(workflows, schemes, fields, JQL) — under its own name.

**Current milestone:** M0 Foundations — the platform runs, stops, starts and recovers; no product
features yet. See [MILESTONES.md](MILESTONES.md).

## Quick start

One-time desktop setup (hosts entries, trusting the local CA): [docs/runbooks/desktop-setup.md](docs/runbooks/desktop-setup.md).

```bash
scripts/init-env.sh          # create .env with generated secrets
docker compose up -d --wait  # build and start the stack
scripts/smoke-test.sh        # verify it end to end
```

Then open <https://tracker.test:8443>.

| URL | What |
|---|---|
| https://tracker.test:8443 | App (SPA + REST API under `/api/v1`) |
| https://tracker.test:8443/api/v1/openapi.json | OpenAPI spec |
| https://auth.test:8443/admin/ | Keycloak admin console |

## Documentation

| Doc | Contents |
|---|---|
| [FEATURES.md](FEATURES.md) | Scope, non-functional requirements, tiers, decisions |
| [DOMAIN_MODEL.md](DOMAIN_MODEL.md) | Entities, modelling decisions, invariants |
| [MILESTONES.md](MILESTONES.md) | P0 milestones and exit criteria |
| [TECH_STACK.md](TECH_STACK.md) | Architecture, stack, memory budget |
| [docs/adr/](docs/adr/README.md) | Architecture decision records |
| [docs/runbooks/](docs/runbooks/) | Desktop setup, operations, observability |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Ground rules and local development |

## Stack at a glance

Java 21 + Spring Boot 4.1 modular monolith · PostgreSQL 18 · Keycloak 26 (OIDC) · React 19 + Vite
SPA · Caddy (local TLS) · Docker Compose, whole stack under 3 GB RAM.
