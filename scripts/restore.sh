#!/usr/bin/env bash
# Restores both databases from a backup directory, replacing ALL current data.
#   scripts/restore.sh backups/<timestamp> --yes
source "$(dirname "$0")/lib.sh"

dir="${1:-}"
[[ -n "$dir" && -d "$dir" ]] || fail "usage: scripts/restore.sh <backup-dir> --yes"
[[ "${2:-}" == "--yes" ]] || fail "this replaces all current data; re-run with --yes to confirm"

(cd "$dir" && grep -E '\.dump$' MANIFEST | sha256sum --check --quiet) || fail "checksum mismatch in $dir"

log "Stopping services that use the databases"
compose stop caddy app keycloak

for db in tracker keycloak; do
    log "Restoring $db"
    compose exec -T postgres dropdb --username postgres --force --if-exists "$db"
    compose exec -T postgres createdb --username postgres --owner "$db" "$db"
    compose exec -T postgres psql --username postgres -d postgres -qc "REVOKE ALL ON DATABASE $db FROM PUBLIC"
    compose exec -T postgres pg_restore --username postgres --dbname "$db" --exit-on-error < "$dir/$db.dump"
done

log "Starting services"
compose up -d --wait
log "Restore complete from $dir"
