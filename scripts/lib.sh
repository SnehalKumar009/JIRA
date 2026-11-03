#!/usr/bin/env bash
# Shared helpers for the operational scripts. Source it; don't run it.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CA_CERT="$REPO_ROOT/certs/tracker-local-ca.crt"
HTTPS_PORT="$(grep -E '^TRACKER_HTTPS_PORT=' "$REPO_ROOT/.env" 2>/dev/null | cut -d= -f2)"
HTTPS_PORT="${HTTPS_PORT:-8443}"
TRACKER_URL="https://tracker.test:$HTTPS_PORT"
AUTH_URL="https://auth.test:$HTTPS_PORT"

compose() {
    docker compose --project-directory "$REPO_ROOT" "$@"
}

log() {
    printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*" >&2
}

fail() {
    printf 'FAIL: %s\n' "$*" >&2
    exit 1
}

# Export Caddy's local root CA so scripts can verify TLS without touching the system trust store.
export_ca() {
    mkdir -p "$REPO_ROOT/certs"
    compose cp caddy:/data/caddy/pki/authorities/local/root.crt "$CA_CERT" >/dev/null
}

# HTTPS request to the stack exactly as a browser would make it: real hostnames, verified TLS.
# Works before /etc/hosts is edited because --resolve pins the names to 127.0.0.1.
https_get() {
    [[ -f "$CA_CERT" ]] || export_ca
    curl --silent --show-error --fail-with-body \
         --cacert "$CA_CERT" \
         --resolve "tracker.test:$HTTPS_PORT:127.0.0.1" \
         --resolve "auth.test:$HTTPS_PORT:127.0.0.1" \
         "$@"
}

installation_id() {
    https_get "$TRACKER_URL/api/v1/system/info" | sed -E 's/.*"installationId":"([^"]+)".*/\1/'
}
