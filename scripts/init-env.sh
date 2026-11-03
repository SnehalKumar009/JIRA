#!/usr/bin/env bash
# Creates .env from .env.example, replacing every CHANGE_ME with a random secret.
# Refuses to overwrite an existing .env — secrets in it are already baked into the database volume.
source "$(dirname "$0")/lib.sh"

ENV_FILE="$REPO_ROOT/.env"
[[ -e "$ENV_FILE" ]] && { log ".env already exists — leaving it untouched"; exit 0; }

umask 077
while IFS= read -r line; do
    while [[ "$line" == *CHANGE_ME* ]]; do
        line="${line/CHANGE_ME/$(openssl rand -hex 24)}"
    done
    printf '%s\n' "$line"
done < "$REPO_ROOT/.env.example" > "$ENV_FILE"

log "Created .env with generated secrets (mode 600). Keep it safe: losing it means losing database access."
