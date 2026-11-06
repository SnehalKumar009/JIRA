#!/usr/bin/env bash
# Regenerates the committed jOOQ classes (backend/src/main/jooq) from the Flyway migrations (ADR-0022):
# starts a throwaway PostgreSQL, migrates it, runs jOOQ codegen, removes the container.
# CI runs this and fails if the generated code differs from what is committed.
source "$(dirname "$0")/lib.sh"

image="$(sed -n 's:.*<jooq-codegen.postgres-image>\(.*\)</jooq-codegen.postgres-image>.*:\1:p' "$REPO_ROOT/backend/pom.xml")"
password="$(openssl rand -hex 16)"
container="tracker-jooq-codegen-$$"

log "Starting $image"
docker run -d --rm --name "$container" -e POSTGRES_PASSWORD="$password" -p 127.0.0.1::5432 "$image" > /dev/null
trap 'docker stop "$container" > /dev/null' EXIT
port="$(docker port "$container" 5432/tcp | head -1 | sed 's/.*://')"
until docker exec "$container" pg_isready -U postgres -q; do sleep 1; done

rm -rf "$REPO_ROOT/backend/src/main/jooq"
(cd "$REPO_ROOT/backend" && ./mvnw -B -q -Pjooq-codegen generate-sources \
    -Dcodegen.db.url="jdbc:postgresql://127.0.0.1:$port/postgres" \
    -Dcodegen.db.user=postgres -Dcodegen.db.password="$password")
log "jOOQ classes regenerated in backend/src/main/jooq"
