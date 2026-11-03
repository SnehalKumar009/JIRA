#!/usr/bin/env bash
# M0 exit criterion: `docker compose down` / `up` loses no data.
source "$(dirname "$0")/lib.sh"

before="$(installation_id)"
log "Installation id before: $before"
compose down
compose up -d --wait
after="$(installation_id)"
log "Installation id after:  $after"
[[ "$before" == "$after" ]] || fail "data lost across down/up"
log "Persistence test passed"
