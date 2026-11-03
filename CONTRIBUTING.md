# Contributing

## Ground rules

- **Definition of Done** ([FEATURES.md §7](FEATURES.md)) applies to every change: tests, server-side
  authorization, migrations, docs. Nothing is "done except tests".
- **Module boundaries** ([ADR-0001](docs/adr/0001-modular-monolith.md)): each direct sub-package of
  `dev.tracker` is a module. Use another module only through its top-level package; never its
  sub-packages. `ModularityTests` enforces this.
- **Schema changes** are new Flyway migrations (`backend/src/main/resources/db/migration`), never
  edits to applied ones. Keep them forward-compatible (expand → migrate → contract).
- **Significant decisions** get an ADR in [`docs/adr/`](docs/adr/README.md). To change an accepted
  decision, add a new ADR that amends or supersedes it.
- **No secrets in git.** Configuration comes from the environment; local secrets live in `.env`.

## Local development

| Task | Command |
|---|---|
| Backend tests (needs Docker) | `cd backend && ./mvnw verify` |
| Backend against a throwaway database | `cd backend && ./mvnw spring-boot:test-run` |
| Frontend dev server (proxies `/api` to :8080) | `cd frontend && npm ci && npm run dev` |
| Frontend typecheck and build | `cd frontend && npm run build` |
| Full stack | `docker compose up -d --wait` |
| Full-stack checks | `scripts/smoke-test.sh`, `scripts/test-persistence.sh`, `scripts/measure-memory.sh` |

## CI

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on every pull request: backend tests,
frontend build, secret scan, dependency scan, and the full stack with the M0 checks and an image
scan. All jobs must pass before merging.
