# P0 Milestones — Jira-style Issue Tracker

**Status:** Accepted 2026-10-28
**Last updated:** 2026-10-28
**Related:** [FEATURES.md](FEATURES.md) · [DOMAIN_MODEL.md](DOMAIN_MODEL.md)

Technology-neutral. Tech stack is chosen after this plan is agreed, and coding starts only after that.

---

## 1. How the milestones are cut

1. **Walking skeleton first.** M0 builds the whole platform end to end — stack, CI, migrations, logging, backup — with almost no features. Every later milestone adds features to a base that is already production grade.
2. **Vertical slices.** Each milestone delivers working behaviour through every layer: data model, domain rules, REST API, UI where relevant, tests, docs.
3. **Every milestone meets the Definition of Done** in [FEATURES.md §7](FEATURES.md). Nothing is "done except tests".
4. **Every milestone ends in a tagged, runnable release** that survives `docker compose down` / `up`.
5. **Dependency order.** Each milestone only builds on earlier ones.
6. **Seed data grows with the features.** From M4 on, the seed script generates the full target-scale dataset (100 projects × ~1k issues) for everything built so far.

## 2. Overview

```
M0 Foundations ─▶ M1 Identity ─▶ M2 Projects & access ─▶ M3 Configuration engine
                                                                │
              M7 Hardening & release ◀─ M6 Kanban board ◀─ M5 JQL search ◀─ M4 Issues
```

| # | Milestone | Delivers | Relative size |
|---|---|---|---|
| M0 | Foundations | Running, observable, backed-up empty platform | M |
| M1 | Identity | Login, users, groups, PATs, audit log | M |
| M2 | Projects & access | Projects, roles, permission model | M |
| M3 | Configuration engine | Issue types, statuses, versioned workflows, field registry | L |
| M4 | Issues | Full issue lifecycle with history | L |
| M5 | JQL search | Parser, permission-filtered search | L |
| M6 | Kanban board | Board, drag-and-drop transitions, LexoRank | M |
| M7 | Hardening & release | P0 release candidate | M |

Sizes are relative to each other, not time estimates.

---

## 3. Milestones

### M0 — Foundations (walking skeleton)

**Status:** Done — all exit criteria met; CI green on GitHub since commit 8b5dbf0 (2026-11-03).

**Goal:** the whole platform runs, stops, starts, and recovers — before any features exist.

**Scope**
- Repository layout, contribution rules, ADR folder
- Docker Compose stack: app, database, Keycloak, reverse proxy with TLS (local certificate)
- Configuration via environment; untracked `.env` with a committed `.env.example`
- Schema migration framework, applied idempotently on startup
- Structured JSON logging with request IDs; health and readiness endpoints
- Graceful shutdown on `SIGTERM`; Compose healthchecks and startup ordering
- OpenTelemetry instrumentation hooks; optional observability Compose profile
- Backup and restore scripts (database + attachment storage)
- CI pipeline: build, unit tests, dependency scan, image scan, image tagging
- OpenAPI skeleton served by the app; consistent API error format
- Security headers (CSP, HSTS)

**Exit criteria**
- One command brings the stack up; app answers over HTTPS
- `docker compose down` / `up` and a host reboot lose no data
- Backup → wipe → restore returns the system to the same state (scripted test)
- CI is green and blocks merges on failure
- Measured memory stays within the per-service limits in [ADR-0016](docs/adr/0016-memory-budget.md) (core stack < 2 GB)

---

### M1 — Identity

**Status:** Built 2026-11-06 — exit criteria verified against the stack (`scripts/test-login.sh`) and in integration tests with a real Keycloak. One finding: Keycloak sends no back-channel logout when a user is *disabled*, so that case ends the app session at the next token refresh (≤ 5 minutes) rather than instantly; signing out the user's sessions in Keycloak is instant (ADR-0025).

**Goal:** real users can log in securely; machines can call the API.

**Scope**
- Keycloak realm configuration as code, imported on startup; Keycloak in production mode on Postgres
- OIDC Authorization Code + PKCE; **BFF** session cookie (`HttpOnly; Secure; SameSite`)
- Token validation (issuer, audience, expiry, JWKS caching and rotation)
- Logout, plus back-channel logout from Keycloak
- User created on first login (keyed on `sub`); profile refreshed on each login
- Groups and memberships synced from the token on login
- User deactivation (never delete)
- Personal access tokens: create (shown once), hashed storage, scopes, expiry, revoke, last-used
- Audit log: logins, logouts, token create / revoke
- API rate limiting; brute-force protection enabled in Keycloak
- UI app shell: login, logout, current-user menu

**Exit criteria**
- Log in through Keycloak, see the app shell, log out
- Disabling the user in Keycloak ends their session in the app
- A PAT can call the API; a revoked or expired one cannot
- Audit log shows each of the above

---

### M2 — Projects & access

**Goal:** projects exist and the permission model decides who can do what.

**Scope**
- Projects: create, edit, archive; key validation; project key history
- Components
- Project roles (Administrator, Member, Viewer) as global definitions
- Role actors: assign users and groups to roles per project
- Default permission scheme stored as data (permission → role grants)
- Permission service: *can user U do P in project X* (D11), with per-user-per-project cache and invalidation
- Permission checks on every project endpoint
- Audit log: project, role-actor, and permission-scheme changes
- UI: project list, project settings (details, components, people / roles)

**Exit criteria**
- Authorization test matrix (each role × each permission) passes, via both session and PAT
- Changing a user's group membership in Keycloak changes their access after next login
- Cache invalidation tested: role change takes effect immediately

---

### M3 — Configuration engine

**Goal:** the "scheme" layer works — projects get their issue types, workflows, and fields through schemes.

**Scope**
- Issue types with levels (Epic 1, Story / Task / Bug 0, Sub-task −1); issue type schemes
- Statuses with categories; priorities; resolutions
- Workflows as immutable published versions with drafts (D3)
- Transitions with **conditions** (role-based, assignee-only) and **validators** (field required, resolution set)
- Workflow schemes (issue type → workflow version)
- Field registry: system fields defined; custom-field definitions and contexts modelled but **not exposed** (decision 4)
- Default configuration seeded: standard issue types, statuses, a simple workflow and a dev workflow with review, default schemes — new projects work out of the box
- Admin REST API for all of the above
- Audit log for every configuration change

**Exit criteria**
- Create a workflow via the API, publish it, attach it to a project through a workflow scheme
- Invalid configurations are rejected (e.g. a scheme referencing a draft, a workflow with no initial status)
- Publishing a version that would orphan statuses in use is **blocked** (see decision MD2)

---

### M4 — Issues

**Goal:** the full issue lifecycle, correctly audited.

**Scope**
- Create, view, edit, soft-delete issues
- Concurrency-safe key allocation (per-project counter)
- System fields; hierarchy rules (D5); issue type must belong to the project's scheme
- Optimistic locking — conflicting edits get a clear conflict response
- Transitions executed by the workflow engine: conditions → validators → status change → history, atomically
- Change history (`ChangeGroup` / `ChangeItem`, raw + display values) in the same transaction
- Comments (create, edit, delete) with history
- Rich text: structured, sanitised format
- Outbox events written for every change (consumers come in P1)
- Seed script: target-scale dataset (100 projects × ~1k issues, realistic history)
- UI: create issue, issue view (fields, comments, history tab, transition buttons), edit inline

**Exit criteria**
- Parallel-create test produces no duplicate or skipped keys
- Concurrent-edit test produces a conflict, never a silent overwrite
- Every invariant in [DOMAIN_MODEL.md §5](DOMAIN_MODEL.md) that applies to issues has a test
- History reads correctly after renaming a priority and deactivating a user
- Issue view works against the seeded dataset

---

### M5 — JQL search

**Goal:** a real query language over issues, filtered by permissions.

**Scope**
- Lexer → parser → AST → parameterised query; never string-built queries
- Operators: `=`, `!=`, `IN`, `NOT IN`, `AND`, `OR`, parentheses, `ORDER BY`
- Fields resolved through the field registry (system fields in P0)
- Functions: `currentUser()`, plus a small set agreed during the milestone
- Permission filter injected into every query (D11)
- Result limits, cursor pagination, query timeout
- Error messages with position (`Unexpected token at column 23`)
- Filter entity: saved, **private** filters (needed by boards); sharing and filter management in P1
- Indexes for common query shapes, validated against the seeded dataset
- UI: search page with JQL input and results table

**Exit criteria**
- Parser test suite covering valid and invalid queries
- Permission test: a user never sees issues from projects they can't browse, via search or direct fetch
- Common queries return bounded results on the seeded dataset

---

### M6 — Kanban board

**Goal:** the classic board, built on everything before it.

**Scope**
- Board defined by a filter (M5); visibility follows permission filtering
- Columns mapped to statuses; WIP limits (shown and warned)
- Drag across columns runs the workflow transition, honouring conditions and validators
- Drag within a column re-ranks using **LexoRank** (D9), including rebalancing
- Quick filters (JQL)
- Swimlanes: none, by assignee, by epic
- Board configuration via API and UI

**Exit criteria**
- A move that a condition or validator forbids is rejected with a clear reason
- Re-ranking updates a single issue; rebalancing tested
- Board loads within bounds on a project from the seeded dataset

---

### M7 — Hardening & P0 release

**Goal:** a release candidate you'd trust with real data.

**Scope**
- End-to-end test suite for the main user journeys
- Security review against OWASP Top 10; scan findings resolved
- Restore drill from backup on a clean machine
- Runbooks: start / stop, backup / restore, Keycloak administration, rotating secrets, upgrading
- API documentation published from OpenAPI
- ADRs complete for every major decision
- P0 release tag

**Exit criteria**
- Every P0 feature meets the Definition of Done
- Restore drill passes from documentation alone
- No open critical / high security findings

---

## 4. Decisions for this plan

| # | Decision | Chosen | Status |
|---|---|---|---|
| MD1 | Admin UI in P0 | **API + config-as-code only in P0**; admin UI for workflows / schemes in P1. Project settings and board configuration *do* get UI in P0 | Decided 2026-10-28 |
| MD2 | Editing workflows in use | **P0 allows only non-destructive changes** (add statuses / transitions); changes that orphan in-use statuses are blocked until status migration lands in P2 | Decided 2026-10-28 |
| MD3 | Saved filters in P0 | **Private filters only** (boards need them); sharing and filter management in P1 | Decided 2026-10-28 |
| MD4 | Default configuration | **Ship Jira-like defaults** so a new project works without any admin setup | Decided 2026-10-28 |
| MD5 | Custom fields in P0 | **Modelled in the field registry, not exposed** — consistent with decision 4 | Decided 2026-10-28 |
| MD6 | Swimlanes in P0 | **None / assignee / epic** only; custom JQL swimlanes later | Decided 2026-10-28 |
