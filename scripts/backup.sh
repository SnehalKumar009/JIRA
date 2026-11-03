#!/usr/bin/env bash
# Online backup of both databases into backups/<timestamp>/ (ADR-0004).
#   scripts/backup.sh            -> prints the backup directory on stdout
# Attachments join the backup when attachment storage arrives in P1.
source "$(dirname "$0")/lib.sh"

stamp="$(date +%Y%m%d-%H%M%S)"
dir="$REPO_ROOT/backups/$stamp"
umask 077
mkdir -p "$dir"

for db in tracker keycloak; do
    log "Dumping $db"
    compose exec -T postgres pg_dump --username postgres --format custom --dbname "$db" > "$dir/$db.dump"
    # A dump that pg_restore can't list is not a backup.
    compose exec -T postgres pg_restore --list < "$dir/$db.dump" > /dev/null \
        || fail "backup of $db is not readable"
done

{
    echo "created_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "postgres_image=$(docker inspect --format '{{.Config.Image}}' "$(compose ps -q postgres)")"
    echo "schema_version=$(compose exec -T postgres psql -U postgres -d tracker -tAc \
        'SELECT max(version::int) FROM flyway_schema_history WHERE success')"
    (cd "$dir" && sha256sum ./*.dump)
} > "$dir/MANIFEST"

log "Backup complete: $dir"
echo "$dir"
