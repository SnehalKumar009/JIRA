# ADR-0018: Maven and repository layout

- **Status:** Accepted
- **Date:** 2026-10-28

## Context
The build tool was not chosen in the planning phase. M0 needs one, plus a layout that keeps the
backend, frontend and infrastructure apart while building into one image (ADR-0001, ADR-0010).

## Decision
- **Maven** with the Maven Wrapper (`backend/mvnw`), Spring Boot parent POM.
- Layout:

  | Path | Contents |
  |---|---|
  | `backend/` | Spring Boot application (`dev.tracker`), one package per module |
  | `frontend/` | React + Vite SPA |
  | `infra/` | Per-service configuration: Keycloak image, Postgres init, Caddy, observability |
  | `scripts/` | Operational scripts (env, backup, restore, verification) |
  | `docs/` | ADRs and runbooks |
  | `Dockerfile`, `compose.yaml` | App image (frontend + backend) and the full stack |

- The app image is built by a multi-stage `Dockerfile` at the root: Node builds the SPA, Maven
  packages it into the jar's static resources, a JRE stage runs it.

## Alternatives considered
- **Gradle** — faster incremental builds and JDK toolchain provisioning; more build logic to own.
- **Maven building the frontend** (frontend-maven-plugin) — couples the two builds; the Dockerfile
  already composes them.

## Consequences
- Declarative, conventional build; everything Maven needs is in `pom.xml`.
- Frontend and backend build independently in CI and together in the image.
