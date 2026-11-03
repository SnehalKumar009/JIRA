#!/usr/bin/env bash
# M0 exit criterion: backup -> wipe -> restore returns the system to the same state.
# DESTRUCTIVE: deletes the postgres-data volume (after taking a verified backup). Needs --yes.
source "$(dirname "$0")/lib.sh"
[[ "${1:-}" == "--yes" ]] || fail "this wipes the database volume and restores it; re-run with --yes"

original="$(installation_id)"
log "Installation id: $original"

backup_dir="$("$REPO_ROOT/scripts/backup.sh")"

log "Wiping the database volume"
compose down
docker volume rm tracker_postgres-data > /dev/null
compose up -d --wait
fresh="$(installation_id)"
[[ "$fresh" != "$original" ]] || fail "volume was not actually wiped"
log "Fresh database has a new installation id: $fresh"

"$REPO_ROOT/scripts/restore.sh" "$backup_dir" --yes
restored="$(installation_id)"
[[ "$restored" == "$original" ]] || fail "restored id $restored != original $original"

https_get "$AUTH_URL/realms/master/.well-known/openid-configuration" > /dev/null \
    || fail "Keycloak not serving after restore"
log "Backup/restore test passed (backup kept at $backup_dir)"
