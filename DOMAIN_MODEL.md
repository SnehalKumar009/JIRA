# Domain Model — Jira-style Issue Tracker

**Status:** Accepted 2026-10-28 — technology-neutral (no storage or framework choices)
**Related:** [FEATURES.md](FEATURES.md)

Physical storage choices (tables vs documents, how custom-field values are laid out, indexes) are deferred to the architecture and tech stack step. This document describes *what* exists and the rules between things, not *how* it is stored.

---

## 1. The core pattern: global objects + schemes + projects

Most of Jira's configurability comes from one indirection:

```
Project ──uses──▶ Scheme ──maps──▶ Global objects
  PROJ            Workflow scheme     Bug   → "Dev workflow"
                                      Story → "Dev workflow"
                                      Epic  → "Simple workflow"
```

- **Global objects** are defined once and shared: statuses, issue types, priorities, resolutions, fields, workflows, project roles.
- **Schemes** are reusable mappings: "issue type X uses workflow Y", "permission Z is granted to role W".
- **Projects** hold no configuration of their own; they point at schemes.

Result: 100 projects can share one scheme — change it once, all 100 follow. A project that needs to differ gets its own scheme.

---

## 2. Entity map

```
IDENTITY & ACCESS                       CONFIGURATION (global + schemes)
─────────────────                       ─────────────────────────────────
User ──< GroupMembership >── Group      IssueType (level: 1 epic, 0 standard, -1 subtask)
User ──< PersonalAccessToken            Status ──▶ StatusCategory (To Do / In Progress / Done)
ProjectRole (Admin, Member, Viewer)     Workflow (versioned) ──< Transition (from → to, rules)
RoleActor: (Project, Role) → User|Group Priority, Resolution
PermissionScheme ──< Grant (permission → role)
                                        FieldDefinition (system | custom, type)
PROJECT                                 FieldContext (applies to project/issue type, options, required)
───────                                 IssueTypeScheme · WorkflowScheme · PermissionScheme
Project ──▶ schemes above
Project ──< Component, Version          ISSUE
Project ──< ProjectKeyHistory           ─────
Project ── IssueCounter                 Issue ──▶ Project, IssueType, Status, Priority, Resolution
                                        Issue ──▶ parent Issue (hierarchy)
BOARD & SEARCH                          Issue ──< CustomFieldValue ──▶ FieldDefinition
──────────────                          Issue ──< Comment, Watcher, Vote, Attachment, Label
Filter (owner, JQL, shares)             Issue ──< IssueLink >── Issue   (──▶ IssueLinkType)
Board ──▶ Filter                        Issue ──< ChangeGroup ──< ChangeItem
Board ──< Column (→ statuses, WIP)      IssueKeyHistory (old keys after moves)
Board ──< QuickFilter (JQL)
                                        PLATFORM
                                        ────────
                                        AuditEvent (append-only) · OutboxEvent (pending side effects)
```

`──<` = one-to-many · `>──<` = many-to-many · `──▶` = refers to

---

## 3. Entities by area

Attributes listed are the essential ones; every entity also has an immutable internal ID and created / updated timestamps unless noted.

### 3.1 Identity & access

| Entity | Key attributes | Notes |
|---|---|---|
| **User** | idp_subject (OIDC `sub`), display_name, email, active, anonymised | Created on first login (SCIM in P1). Never deleted |
| **Group** | name, source (IdP) | Global, organisational. Synced from the IdP |
| **GroupMembership** | user, group | Synced from the IdP |
| **PersonalAccessToken** | user, name, token_hash, scopes, expires_at, last_used_at, revoked_at | Token value shown once, stored only as a hash |
| **ProjectRole** | name, description | Global definition (Administrator, Member, Viewer) |
| **RoleActor** | project, role, user *or* group | Who fills a role in a given project |
| **PermissionScheme** | name | Shared by many projects |
| **Grant** | scheme, permission, role | e.g. *Transition issues → Member* |

### 3.2 Projects

| Entity | Key attributes | Notes |
|---|---|---|
| **Project** | key, name, type, lead, archived, issue_type_scheme, workflow_scheme, permission_scheme | Holds no config of its own — points at schemes |
| **ProjectKeyHistory** | project, old_key | Old keys keep resolving after a rename |
| **IssueCounter** | project, next_number | Allocates issue numbers safely under concurrency |
| **Component** | project, name, lead | |
| **Version** | project, name, release_date, released, archived | P1 |

### 3.3 Configuration

| Entity | Key attributes | Notes |
|---|---|---|
| **IssueType** | name, level, icon | Epic = 1, Story / Task / Bug = 0, Sub-task = −1 |
| **IssueTypeScheme** | name, issue types, default type | Which types a project offers |
| **Status** | name, category | Global — shared across workflows |
| **StatusCategory** | To Do / In Progress / Done | Fixed set; reports and "done" logic use this, never status names |
| **Workflow** | name, version, statuses, initial status, published / draft | Immutable once published |
| **Transition** | workflow version, name, from statuses, to status, conditions, validators, post-functions | Rules stored as *type + parameters* |
| **WorkflowScheme** | name, issue type → workflow version, default workflow | |
| **Priority** | name, order, icon | Global |
| **Resolution** | name | Global (Done, Won't Do, Duplicate…) |
| **FieldDefinition** | key, name, kind (system / custom), value type | Value types: text, rich text, number, date, user, select, multi-select, labels |
| **FieldContext** | field, projects, issue types, options, default, required | Where a custom field applies and how it behaves there |

### 3.4 Issues

| Entity | Key attributes | Notes |
|---|---|---|
| **Issue** | project, number, issue_type, status, summary, description, priority, resolution, assignee, reporter, parent, rank, labels, components, due_date, story_points, resolved_at, deleted_at, version | `version` is for optimistic locking; key derived as `project.key-number` |
| **IssueKeyHistory** | issue, old_key | Written when an issue moves project |
| **CustomFieldValue** | issue, field, typed value | One logical value (or set of values) per issue per field |
| **Comment** | issue, author, body (rich text), edited_at, deleted_at | |
| **Watcher** / **Vote** | issue, user | |
| **IssueLinkType** | name, outward text, inward text | e.g. *blocks* / *is blocked by* |
| **IssueLink** | type, source issue, target issue | Directional |
| **Attachment** | issue, uploader, file name, content type, size, storage reference | P1 |
| **ChangeGroup** | issue, author, timestamp | One per user action |
| **ChangeItem** | change group, field, old raw value, old display text, new raw value, new display text | One per field changed |

### 3.5 Boards & search

| Entity | Key attributes | Notes |
|---|---|---|
| **Filter** | name, owner, JQL, shares | Boards are built on filters |
| **Board** | name, type (Kanban), filter | Visibility follows the filter's permission filtering |
| **Column** | board, name, order, statuses, WIP min / max | One status maps to at most one column per board |
| **QuickFilter** | board, name, JQL | |

### 3.6 Platform

| Entity | Key attributes | Notes |
|---|---|---|
| **AuditEvent** | actor, action, target, before / after summary, timestamp, request ID | Append-only |
| **OutboxEvent** | event type, payload, created_at, processed_at, attempts | Written in the same transaction as the change; processed asynchronously (notifications, webhooks) |

---

## 4. Modelling decisions

| # | Decision | Rule | Why |
|---|---|---|---|
| **D1** | Issue identity | Immutable internal ID is the identity; the key `PROJ-123` is a derived label. Numbers come from a per-project counter, allocated safely under concurrency. Old keys go to `IssueKeyHistory` | Keys change when issues move project; links and bookmarks must keep working |
| **D2** | Statuses are global | A status is one shared object used by many workflows; each belongs to a status category | Makes cross-project JQL (`status = "In Progress"`) and board column mapping work; reports rely on categories, not names |
| **D3** | Workflows are immutable versions | Editing creates a new version; schemes point to a version; issues store only their status. Publishing a version that drops an in-use status requires a status mapping. **Invariant:** an issue's status always exists in its current workflow | Workflows can change while issues are live without corrupting them |
| **D4** | Transition rules are configuration | Conditions, validators, post-functions are each a *type + parameters*, executed by the engine | Rules are data, not code; becomes the extension point for automation |
| **D5** | Hierarchy by issue-type level | Optional `parent` on Issue; **a parent is exactly one level above its child**; sub-tasks share their parent's project | One simple rule covers Epic → Story → Sub-task and allows deeper levels later |
| **D6** | System vs custom fields | System fields are real attributes of Issue; custom fields are `FieldDefinition` + `FieldContext` + typed `CustomFieldValue`; a **field registry** presents both uniformly to JQL, screens, and history | Fast, typed core fields plus unlimited configurable ones, behind one query language. Physical layout decided with the tech stack |
| **D7** | Change history is an append-only log | `ChangeGroup` + `ChangeItem`, storing raw value *and* display text, written in the same transaction as the change. **Not** event sourcing | History stays readable after renames / anonymisation; reports read from it; far simpler than event sourcing |
| **D8** | Users referenced by internal ID | Never by username, email, or IdP `sub` (the `sub` lives only on User) | Anonymisation, deactivation, and IdP migration each touch one row |
| **D9** | Single global rank | One LexoRank value per issue; boards and backlog sort by it | Consistent ordering across boards showing the same issues; moving a card updates one row |
| **D10** | Deletion semantics | Issues soft-deleted (recoverable, audited); users deactivated; in-use statuses, fields, workflows retired rather than deleted | Production-grade safety; stricter than Jira, which hard-deletes issues |
| **D11** | Permission resolution via roles | *Can U do P in project X?* → project's permission scheme → roles holding P → is U in those roles for X (directly or via a group). Cached per user + project; invalidated on role / membership change. Issue security (P2) adds a second check on the same path | Permissions never name people; same resolution runs inside search queries |

---

## 5. Invariants

Rules that must hold at all times, enforced by the domain layer and covered by tests:

1. An issue key is unique across the system at any moment; numbers within a project are never reused.
2. An issue's status exists in the workflow version its project's workflow scheme assigns to its issue type.
3. An issue's type is in its project's issue type scheme.
4. A parent issue's type level is exactly one above the child's; a sub-task's parent is in the same project.
5. Every change to an issue produces exactly one `ChangeGroup` in the same transaction.
6. A resolution is set if and only if the status category is Done (enforced by default validators / post-functions).
7. A status maps to at most one column on a given board.
8. Published workflow versions are never modified.
9. Users, and statuses / fields / workflows that are in use, are never hard-deleted.
10. Every side effect (notification, webhook) is recorded as an `OutboxEvent` in the same transaction as the change that caused it.

---

## 6. Hard parts (main learning targets)

| Piece | Why it's hard |
|---|---|
| Workflow versioning + status migration (D3) | Changing a workflow while issues are live |
| Field registry + custom fields (D6) | One query language over two storage models |
| Permission resolution (D11) | Must run inside every search, not afterwards |
| Change history display (D7) | Must stay readable after renames and anonymisation |

---

## 7. Deferred to the tech stack step

- Physical layout of custom-field values (separate typed value records vs a document column)
- Rank implementation details (LexoRank bucket and rebalancing strategy)
- Index design for JQL
- Where permission caches live
- How rich text is represented and sanitised
