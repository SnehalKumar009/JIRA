#!/usr/bin/env bash
# Checks the running stack the way a browser sees it: verified TLS, real hostnames, security
# headers, Keycloak issuer, and that internals are not exposed.
source "$(dirname "$0")/lib.sh"
export_ca

headers="$(https_get -D - -o /dev/null "$TRACKER_URL/api/v1/system/info")"
grep -qi '^strict-transport-security: max-age=31536000' <<<"$headers" || fail "HSTS header missing"
grep -qi "^content-security-policy: default-src 'self'" <<<"$headers" || fail "CSP header missing"
grep -qi '^x-request-id: ' <<<"$headers" || fail "X-Request-Id header missing"
grep -qi '^server:' <<<"$headers" && fail "Server header should be stripped"
log "API over HTTPS with security headers: ok"

id="$(installation_id)"
[[ "$id" =~ ^[0-9a-f-]{36}$ ]] || fail "unexpected installation id: $id"
log "Installation id: $id"

https_get "$TRACKER_URL/" | grep -q '<div id="root">' || fail "SPA not served"
log "SPA served: ok"

expected_issuer="\"issuer\":\"$AUTH_URL/realms/master\""
https_get "$AUTH_URL/realms/master/.well-known/openid-configuration" | grep -q "$expected_issuer" \
    || fail "Keycloak issuer is not $AUTH_URL/realms/master"
# Same URL from inside the network must reach the same Keycloak — the OIDC issuer contract (ADR-0012).
compose exec -T app curl -sk "$AUTH_URL/realms/master/.well-known/openid-configuration" \
    | grep -q "$expected_issuer" || fail "app container sees a different issuer"
log "Keycloak issuer identical from desktop and containers: ok"

code="$(https_get -o /dev/null -w '%{http_code}' "$TRACKER_URL/actuator/health" 2>/dev/null || true)"
[[ "$code" == "404" ]] || fail "actuator reachable through the proxy (HTTP $code)"
log "Management endpoints not exposed: ok"

log "Smoke test passed"
