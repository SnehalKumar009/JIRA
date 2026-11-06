#!/usr/bin/env bash
# M1 exit criteria against the running stack, with a throwaway Keycloak user:
#   sign in through Keycloak -> session works; personal access token works, revoked token doesn't;
#   Keycloak logout reaches the app (back-channel); disabling the user in Keycloak ends the session;
#   each step lands in the audit log.
# The temporary user is deleted from Keycloak at the end; the app keeps its (deactivated) record,
# because users are never deleted (D10).
source "$(dirname "$0")/lib.sh"
export_ca

work="$(mktemp -d)"
username="smoke-$(date +%s)-$RANDOM"
password="$(openssl rand -base64 24)"
trap 'cleanup' EXIT

kc() { compose exec -T keycloak /opt/keycloak/bin/kcadm.sh "$@" --config /tmp/kcadm.config; }
cleanup() {
    [[ -n "${kc_user_id:-}" ]] && kc delete "users/$kc_user_id" -r tracker > /dev/null 2>&1 || true
    rm -rf "$work"
}
browser() { https_get --cookie "$work/cookies" --cookie-jar "$work/cookies" "$@"; }
xsrf() { awk '$6 == "XSRF-TOKEN" { print $7 }' "$work/cookies" | tail -1; }
status_of() { browser -o /dev/null -w '%{http_code}' "$@" 2>/dev/null || true; }

compose exec -T keycloak sh -c '/opt/keycloak/bin/kcadm.sh config credentials --config /tmp/kcadm.config \
    --server http://localhost:8080 --realm master \
    --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD"' > /dev/null 2>&1

log "Creating Keycloak user $username in tracker-admins (no MFA enrolment, test only)"
kc_user_id="$(kc create users -r tracker -s "username=$username" -s enabled=true -s "email=$username@example.test" \
    -s firstName=Smoke -s lastName=Test -s emailVerified=true -i)"
kc set-password -r tracker --userid "$kc_user_id" --new-password "$password" > /dev/null
kc update "users/$kc_user_id" -r tracker -s 'requiredActions=[]' > /dev/null
group_id="$(kc get groups -r tracker -q search=tracker-admins --fields id --format csv --noquotes | head -1)"
kc update "users/$kc_user_id/groups/$group_id" -r tracker -n > /dev/null

sign_in() {
    rm -f "$work/cookies"
    browser -L -o "$work/login.html" "$TRACKER_URL/oauth2/authorization/keycloak"
    action="$(grep -o 'id="kc-form-login"[^>]*' "$work/login.html" | grep -o 'action="[^"]*"' | head -1 \
        | sed -e 's/^action="//' -e 's/"$//' -e 's/&amp;/\&/g')"
    [[ -n "$action" ]] || fail "Keycloak login form not found"
    browser -L -o /dev/null --data-urlencode "username=$username" --data-urlencode "password=$password" \
        --data-urlencode credentialId= "$action"
}

sign_in
me="$(browser "$TRACKER_URL/api/v1/me")" || fail "/api/v1/me refused after sign-in"
grep -q "\"username\":\"$username\"" <<<"$me" || fail "unexpected /me: $me"
grep -q '"admin":true' <<<"$me" || fail "tracker-admins membership did not arrive: $me"
grep -q '"authMethod":"SESSION"' <<<"$me" || fail "not a session: $me"
user_id="$(sed -E 's/.*"id":"([^"]+)".*/\1/' <<<"$me")"
log "Signed in through Keycloak; app user $user_id is an admin via IdP group: ok"

created="$(browser -X POST -H "X-XSRF-TOKEN: $(xsrf)" -H 'Content-Type: application/json' \
    -d '{"name":"smoke test","scopes":["read"],"expiresInDays":1}' "$TRACKER_URL/api/v1/me/tokens")" \
    || fail "token creation refused"
token="$(sed -E 's/.*"token":"([^"]+)".*/\1/' <<<"$created")"
token_id="$(sed -E 's/.*"id":"([^"]+)".*/\1/' <<<"$created")"
[[ "$token" == trk_pat_* ]] || fail "unexpected token response: $created"
https_get -H "Authorization: Bearer $token" "$TRACKER_URL/api/v1/me" | grep -q '"authMethod":"TOKEN"' \
    || fail "token not accepted"
code="$(https_get -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $token" \
    -H 'Content-Type: application/json' -d '{"name":"x","scopes":["read"]}' "$TRACKER_URL/api/v1/me/tokens" 2>/dev/null || true)"
[[ "$code" == "403" ]] || fail "a token could manage tokens (HTTP $code)"
log "Access token works for the API and cannot mint tokens: ok"

browser -X DELETE -H "X-XSRF-TOKEN: $(xsrf)" -o /dev/null "$TRACKER_URL/api/v1/me/tokens/$token_id" || fail "revoke refused"
code="$(https_get -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $token" "$TRACKER_URL/api/v1/me" 2>/dev/null || true)"
[[ "$code" == "401" ]] || fail "revoked token still accepted (HTTP $code)"
log "Revoked token refused: ok"

kc create "users/$kc_user_id/logout" -r tracker -b "{}" > /dev/null
sleep 1
[[ "$(status_of "$TRACKER_URL/api/v1/me")" == "401" ]] || fail "session survived Keycloak logout (back-channel)"
log "Keycloak logout ended the app session (back-channel): ok"

sign_in
[[ "$(status_of "$TRACKER_URL/api/v1/me")" == "200" ]] || fail "second sign-in failed"
kc update "users/$kc_user_id" -r tracker -s enabled=false > /dev/null
ended=""
for _ in $(seq 1 70); do   # back-channel is immediate; otherwise the next token refresh (<= 5 min)
    if [[ "$(status_of "$TRACKER_URL/api/v1/me")" == "401" ]]; then ended=yes; break; fi
    sleep 5
done
[[ -n "$ended" ]] || fail "session survived disabling the user in Keycloak"
log "Disabling the user in Keycloak ended the app session: ok"

admin_token_check() {
    # Read the audit trail directly: the test user can no longer sign in to use the admin API.
    compose exec -T postgres psql -U postgres -d tracker -tAc \
        "SELECT string_agg(DISTINCT action, ',') FROM audit_event WHERE actor_user_id = '$user_id' OR target_id IN ('$user_id', '$token_id')"
}
actions="$(admin_token_check)"
for expected in user.create auth.login token.create token.revoke auth.logout.backchannel; do
    grep -q "$expected" <<<"$actions" || fail "audit log is missing $expected (has: $actions)"
done
grep -qE 'auth.session.end|auth.logout.backchannel' <<<"$actions" || fail "session end not audited"
log "Audit log has: $actions"
log "Login test passed"
