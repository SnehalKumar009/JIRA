#!/usr/bin/env bash
# Runs once, when the data volume is first initialised. One database and one login role per
# service, so the app and Keycloak cannot read each other's data (ADR-0004).
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
     -v tracker_password="$TRACKER_DB_PASSWORD" \
     -v keycloak_password="$KEYCLOAK_DB_PASSWORD" <<'SQL'
CREATE ROLE tracker LOGIN PASSWORD :'tracker_password';
CREATE DATABASE tracker OWNER tracker;
REVOKE ALL ON DATABASE tracker FROM PUBLIC;

CREATE ROLE keycloak LOGIN PASSWORD :'keycloak_password';
CREATE DATABASE keycloak OWNER keycloak;
REVOKE ALL ON DATABASE keycloak FROM PUBLIC;
SQL
