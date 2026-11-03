# Runbook: operations

All commands run from the repository root.

## Start and stop

| Task | Command |
|---|---|
| Start (or apply changes) | `docker compose up -d --wait` |
| Stop, keep containers | `docker compose stop` |
| Stop and remove containers (data kept) | `docker compose down` |
| Status | `docker compose ps` |
| Logs (JSON) | `docker compose logs -f app` |
| Rebuild after code changes | `docker compose build && docker compose up -d --wait` |

`docker compose down -v` **deletes all data** (database, local CA, metrics). Don't use it unless
that is the intent.

Services stop gracefully: the app finishes in-flight requests and closes its connection pool
(up to 30 s). Containers restart on crashes (`on-failure`), but do not start by themselves when
Docker starts — run `docker compose up -d --wait` after a reboot.

## Health

| Check | How |
|---|---|
| End-to-end smoke test | `scripts/smoke-test.sh` |
| Memory against limits | `scripts/measure-memory.sh` |
| App readiness (inside the network) | `docker compose exec app curl -s localhost:8081/actuator/health/readiness` |

Health and metrics endpoints are on port 8081, which Caddy does not route — they are not reachable
from the browser by design.

## Backup

```bash
scripts/backup.sh
```

Writes `backups/<timestamp>/` with a custom-format dump of each database (`tracker`, `keycloak`),
verifies each dump is readable, and records checksums, the Postgres image and the schema version in
`MANIFEST`. The stack stays online. `backups/` is git-ignored and mode 700 — copy it off the
machine for real protection.

## Restore

```bash
scripts/restore.sh backups/<timestamp> --yes
```

Verifies checksums, stops the app, Keycloak and Caddy, replaces both databases, and starts
everything again. **All data written after the backup is lost.**

Restore needs the same `.env` as when the backup was taken only for the *database role passwords*
if the volume is recreated; dumps themselves contain no passwords.

## Verifying backups actually work

```bash
scripts/test-backup-restore.sh --yes
```

Takes a backup, deletes the database volume, starts a fresh database, restores, and checks the
installation ID matches. Destructive by design; CI runs it on every change.

## Persistence check

```bash
scripts/test-persistence.sh
```

`docker compose down` / `up` and compares the installation ID.

## Secrets

Secrets live only in `.env`. To rotate one: change it in `.env`, change it in the system that owns
it (e.g. `ALTER ROLE tracker PASSWORD '…'` for a database role), then `docker compose up -d --wait`.
A full rotation procedure arrives with M7.
