# Feature Plan — Jira-style Issue Tracker

**Type:** Production-grade POC
**Status:** Planning — no code yet
**Last updated:** 2026-10-28

---

## 1. Goal

Reverse-engineer how Jira works by building an issue tracker that reproduces its core behaviour — not only the visible product (issues, boards, search) but the **configuration engine** underneath it (configurable workflows, custom fields, roles and permission schemes, JQL). That engine is what makes Jira *Jira* rather than a to-do app.

> The project gets its own name. We reproduce behaviour only — no Atlassian code, logos, or UI assets.

## 2. What "production-grade POC" means

A normal POC proves something *can* work and is thrown away. This one is built to be **safe to run with real users and real data**: secure, observable, recoverable, maintainable.

**Production-grade engineering, desktop-scale infrastructure.** Here "production grade" means the quality of the code and how it's operated: security, correctness, tests, migrations, backups, observability. It does *not* mean high availability or large scale. The whole stack runs on one desktop under Docker Compose and is stopped and started regularly (see §5).

Guiding principles:

1. **Narrow and deep, not wide and shallow.** Small functional scope, full production quality. One hardened feature beats ten demo features.
2. **Non-functional requirements are part of P0**, not "later": security, audit, observability, migrations, tests, CI/CD, backups.
3. **API-first.** Every feature is a versioned REST API first; the UI is just one client of it.
4. **Never hand-roll security primitives.** Authentication, crypto, and session handling come from proven components.
5. **Configuration is data.** Workflows, roles, permissions, and fields are stored and versioned as data, not hard-coded.
6. **Everything as code.** Infrastructure, identity-provider config, schema migrations, dashboards, alerts.

---

## 3. Feature catalog

Each area lists the features, then the production concerns that come with them.

### 3.1 Identity & access

**Approach:** Keycloak as the identity provider, OIDC for login. Our app owns identity records and all authorization; Keycloak owns authentication.

```
Browser ──login──▶ Keycloak ──(federation / brokering)──▶ AD / LDAP / Entra ID / Okta
   │                  │
   │◀── tokens ───────┘   (sub, email, name, groups)
   ▼
Our app (BFF)
   ├─ local user record keyed on `sub` (never on email)
   ├─ group membership synced from IdP (token on login; SCIM in P1)
   └─ authorization: groups → project roles → permissions (all ours)
```

Three separate concerns:

| Concern | Question | Owner |
|---|---|---|
| Authentication | "Prove you are snehal" | Keycloak |
| Identity | "Who is user #42 — name, email, active?" | Our app (issues, history, and JQL all reference users) |
| Authorization | "Can user #42 transition PROJ-7?" | Our app |

**Features**
- Login / logout via OIDC; MFA enforced at the IdP
- Local user records; deactivate, never hard-delete
- Groups (global, organisational) — sourced from the IdP
- Project roles (per project: Administrator, Member, Viewer) — filled with users and/or groups
- Permission model: permissions reference **roles**, never individual users
  `permission ──▶ project role ──(per project)──▶ users / groups`
- Personal access tokens for REST/scripts
- Service accounts for automation (OAuth client-credentials)
- SCIM 2.0 provisioning and deprovisioning
- Enterprise directories (AD / LDAP / Entra ID / Okta) via Keycloak — no app code change
- User anonymisation (GDPR) — keep history, replace personal data

**Production concerns**
- Authorization Code + PKCE; **BFF pattern** — tokens stay server-side, browser gets an `HttpOnly; Secure; SameSite` session cookie; no tokens in `localStorage`
- Token validation: issuer, audience, expiry; JWKS caching and key rotation
- Back-channel logout — disabling a user in the IdP kills their sessions immediately
- PATs stored hashed, scoped, expiring, revocable, with last-used timestamp
- Authorization enforced server-side on every request *and inside every query* (no "hidden on the board but `GET /issues/PROJ-7` works")
- Permission cache with invalidation on role / membership change
- Brute-force protection (Keycloak) and API rate limiting
- Keycloak runs single-node but in **production mode** (`kc.sh start`, not `start-dev`), backed by Postgres so realms and users survive restarts; realm config as code, imported on startup

### 3.2 Projects
**Features:** project with key (`PROJ`) and lead · project types (software, business) · components · versions / releases · archive project

**Production concerns:** project keys unique and immutable once issues exist (or renamed with redirect of old keys, as Jira does) · soft-delete / archive rather than destructive delete

### 3.3 Issues — the core object
**Features**
- Auto-incrementing keys (`PROJ-123`)
- Issue types: Epic, Story, Task, Bug, Sub-task
- Hierarchy: Epic → Story / Task / Bug → Sub-task
- System fields: summary, description (rich text), assignee, reporter, priority, labels, components, fix version, due date, story points
- Custom fields (text, number, select, user picker, date)
- Comments with @mentions
- Attachments
- Issue links: *blocks*, *relates to*, *duplicates*, *clones*
- Watchers and votes
- Change history — every field change (who, when, old → new)
- Work logs / time tracking

**Production concerns**
- Issue-key generation is concurrency-safe (no duplicate or skipped keys under parallel creates)
- Optimistic concurrency on edits (version number / ETag) — two people editing the same issue must not silently overwrite each other
- Change history is append-only and written in the same transaction as the change
- Data model supports custom fields from day one, even though the feature ships in P1
- Rich text stored in a structured, sanitised format (no stored XSS)
- Attachments in object storage (S3-compatible), size limits, content-type checks, malware scanning, access via short-lived signed URLs

### 3.4 Workflows
**Features**
- Statuses, each in a category: To Do / In Progress / Done
- Transitions between statuses
- Transition rules: **conditions** (who may run it), **validators** (e.g. resolution required), **post-functions** (e.g. assign to reporter)
- Workflow per project and issue type

**Production concerns**
- Workflows are versioned; editing a workflow in use requires migrating existing issues to valid statuses (Jira does this with a status-mapping step)
- Transitions are atomic: validators → status change → post-functions → history, all or nothing
- Side effects of post-functions (notifications, webhooks) go through an outbox, not inline

### 3.5 Boards & agile
**Features:** Kanban board (columns mapped to statuses, drag-and-drop, WIP limits, swimlanes, quick filters) · Scrum (backlog, sprints, start / complete, carry-over) · timeline / roadmap

**Production concerns**
- Issue ordering uses a **lexicographic rank** (Jira's LexoRank approach) — moving a card updates one row, not every row below it; includes rebalancing
- Boards are defined by a saved JQL filter, so board visibility obeys the same permission filtering as search
- Board loads are paginated / bounded for large projects

### 3.6 Search (JQL)
**Features:** JQL, e.g. `project = PROJ AND status != Done AND assignee = currentUser() ORDER BY priority DESC` · saved and shared filters · basic search UI that generates JQL

**Production concerns**
- Real parser → AST → parameterised query; never string-concatenated SQL
- Permission filtering applied inside the query
- Query timeouts, result limits, cursor pagination
- Clear, positioned error messages for invalid JQL
- Database indexes are enough at target scale (~100k issues); no separate search engine needed

### 3.7 Reports & dashboards
**Features:** burndown, velocity, cumulative flow, created vs resolved · dashboards of configurable gadgets

**Production concerns:** reports computed from change history, not from current state · heavy reports cached or precomputed

### 3.8 Notifications & activity
**Features:** in-app and email notifications (assigned, mentioned, watched issue changed) · activity stream per project / user

**Production concerns:** delivered asynchronously via a queue (transactional outbox) · batching / digest to avoid notification storms · per-user preferences · respects permissions (no notifying users about issues they can't see)

### 3.9 Automation & integration
**Features:** automation rules (trigger → condition → action) · REST API · webhooks · Git integration (`PROJ-12` in a commit links it)

**Production concerns**
- REST API: versioned (`/api/v1`), OpenAPI spec, cursor pagination, consistent error format, idempotency keys on create
- Webhooks: HMAC-signed payloads, retries with exponential backoff, delivery log, auto-disable on repeated failure
- Automation: loop protection, execution limits per rule, audit of every execution

### 3.10 Admin
**Features:** scheme management (workflow, field configuration, screen, permission, notification) · bulk edit · CSV import / export · audit log

**Production concerns:** audit log is append-only and covers logins, role / permission changes, token creation / revocation, admin config changes · bulk operations run as background jobs with progress and are resumable · imports are idempotent

---

## 4. Non-functional requirements (P0 baseline)

These apply to every feature and are part of its Definition of Done.

| Area | Baseline |
|---|---|
| **Security** | OWASP Top 10 addressed; dependency + container image scanning in CI; secrets in an untracked `.env` / Docker secrets, never in git; TLS terminated at a reverse proxy (local certificate); security headers (CSP, HSTS) |
| **Data** | Versioned schema migrations; data in named Docker volumes (survives `docker compose down` / `up` and host reboot); one-command backup (database dump + attachments) with a **tested restore** script |
| **Observability** | Structured JSON logs with request/trace IDs; health + readiness endpoints; OpenTelemetry instrumentation for metrics and traces, with the observability stack as an optional Compose profile; no alerting (no uptime target) |
| **Reliability** | Clean stop / start: graceful shutdown on `SIGTERM`, Compose healthchecks and startup ordering, migrations applied idempotently on start; stateless app container; idempotent writes where retries are possible; transactional outbox so side effects aren't lost across restarts |
| **Performance** | No latency targets (see §5), but no unbounded queries: every list is paginated and every search has a result limit |
| **Quality** | Unit, integration, and end-to-end tests; CI blocks merges on failure; code review required |
| **Delivery** | Docker Compose, one command brings the whole stack up; images built and tagged in CI; pinned image versions; config via environment; rollback = previous image tag + restore. Kept 12-factor so moving to Kubernetes later needs no app changes |
| **Audit & compliance** | Audit log; data-retention policy; user anonymisation (P2 feature, but the model must allow it) |
| **Documentation** | Architecture decision records (ADRs); API docs from OpenAPI; runbooks for operations |

## 5. Target scale

| Dimension | Target |
|---|---|
| Tenancy | Single organisation (single-tenant) |
| Users | 10 |
| Data | 100 projects × ~1k issues = ~100k issues, ~1 million history rows |
| Hosting | Docker Compose on the developer desktop |
| Availability | No target: the stack is stopped and started regularly |
| Latency | No target |
| Recovery | Data must survive every stop / start and host reboot; backup and restore on demand |

A seed script generates a dataset at this scale so features are always tested against realistic volume, not an empty database.

---

## 6. Tiers

### P0 — Production-grade core

**Functional**
- Identity: Keycloak OIDC login (BFF sessions), local user records, groups from IdP, project roles, personal access tokens
- Permissions: one default permission model (roles → permissions) stored as data
- Projects, components
- Issues: types, hierarchy, system fields, concurrency-safe keys, optimistic locking; data model ready for custom fields
- Comments, change history
- Configurable workflow engine: statuses, categories, transitions, basic conditions and validators
- Kanban board with LexoRank ordering
- JQL subset with permission filtering
- REST API v1 with OpenAPI spec
- Audit log

**Platform**
- Everything in §4

### P1 — Usable product
Scrum (backlog, sprints, burndown) · custom fields · issue links · attachments · @mentions + async notifications · saved / shared filters · webhooks · SCIM provisioning · AD / Entra ID via Keycloak · service accounts · versions / releases

### P2 — Depth
Automation rules · dashboards and gadgets · multiple editable permission and notification schemes · issue security levels · work logs · timeline · Git integration · CSV import / export · bulk edit · user anonymisation · workflow post-functions and workflow migration

### Out of scope
Marketplace / plugins · Confluence-style wiki · Service Management (helpdesk) · multi-tenant SaaS / billing · mobile apps · high availability / multi-node deployment

---

## 7. Definition of Done (every feature)

- [ ] API endpoint(s) documented in OpenAPI
- [ ] Authorization checks enforced server-side and covered by tests
- [ ] Unit + integration tests; E2E test for the main user path
- [ ] Schema changes delivered as migrations (forward-compatible for zero-downtime deploys)
- [ ] Changes recorded in history / audit log where applicable
- [ ] Structured logs, metrics, and traces for the new paths
- [ ] Lists paginated and queries bounded; works against the seeded target-scale dataset
- [ ] No critical / high findings from security scans
- [ ] Runbook / docs updated

---

## 8. Decisions

| # | Decision | Chosen | Status |
|---|---|---|---|
| 1 | Workflow model | **Configurable** — statuses and transitions stored as data, per project | Decided 2026-10-28 |
| 2 | First board type | **Kanban in P0**, Scrum in P1 | Decided 2026-10-28 |
| 3 | Search | **Real JQL parser, small subset**: `=`, `!=`, `IN`, `AND`/`OR`, `ORDER BY`, functions like `currentUser()` | Decided 2026-10-28 |
| 4 | Custom fields | **Feature in P1; P0 data model must allow for them** | Decided 2026-10-28 |
| 5 | Project framing | **Production-grade POC** — narrow scope, full production quality | Decided 2026-10-28 |
| 6 | Authentication | **Keycloak + OIDC**, BFF session pattern | Decided 2026-10-28 |
| 7 | Source of groups | **From the IdP** (token on login, SCIM in P1), so AD / Entra groups flow through | Decided 2026-10-28 |
| 8 | Target scale | **Desktop scale**: 10 users, 100 projects × ~1k issues; no uptime or latency targets (see §5) | Decided 2026-10-28 |
| 9 | Deployment target | **Docker Compose on the developer desktop**, stopped and started regularly. Kept 12-factor so Kubernetes stays possible later | Decided 2026-10-28 |
| 10 | Tech stack | **Java + Spring Boot modular monolith, PostgreSQL, Keycloak, React SPA, Docker Compose under 3 GB** — see [TECH_STACK.md](TECH_STACK.md) and [ADRs](docs/adr/README.md) | Decided 2026-10-28 |

---

## 9. Next planning steps

1. ~~Feature catalog and tiers~~ — done
2. ~~Confirm decisions 6–9~~ — done
3. ~~Domain model~~ — done, see [DOMAIN_MODEL.md](DOMAIN_MODEL.md)
4. ~~P0 milestone breakdown~~ — done, see [MILESTONES.md](MILESTONES.md)
5. ~~Architecture and tech stack~~ — done, see [TECH_STACK.md](TECH_STACK.md) and [ADRs](docs/adr/README.md)
6. **Coding starts with M0** — planning complete
