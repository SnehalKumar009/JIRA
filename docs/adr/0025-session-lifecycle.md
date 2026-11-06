# ADR-0025: Session lifecycle — back-channel logout plus revalidation

- **Status:** Accepted
- **Date:** 2026-11-06

## Context
An app session must end when the user signs out in Keycloak, is disabled there, or is deactivated
in the app. Sessions live in PostgreSQL (ADR-0008) and survive app restarts.

## Decision
- **OIDC back-channel logout:** Keycloak posts a signed logout token to the app, which deletes the
  matching sessions directly from the session store. The link between app sessions and Keycloak
  sessions lives in an `oidc_session` table (Spring's default registry is in memory and would be
  lost on restart). Orphaned links are swept every 10 minutes.
- **Revalidation on every browser request:**
  - the user must still be active in the app (cached 15 s; deactivation clears the cache at once);
  - when the access token has expired (every 5 minutes) it is refreshed; if Keycloak refuses — the
    user was disabled or their Keycloak session ended — the app session ends.
- **Deactivation in the app** ends all the user's sessions immediately (found through the session
  store's principal index) and stops their access tokens.
- Session attributes that no longer deserialize after an upgrade are dropped, so the user just
  signs in again instead of hitting errors.

## Measured behaviour
- Sign-out in Keycloak (or an admin's "Sign out"): app session ends **immediately** (back-channel).
- User **disabled** in Keycloak: Keycloak sends no back-channel logout for this, so the session ends
  at the next token refresh — **within 5 minutes** (about 4 minutes in the M1 test).
- User deactivated in the app: **immediately**.

## Consequences
- For an immediate cut-off when disabling someone in Keycloak, also use "Sign out" on their
  sessions (runbook: users-and-access.md).
- One token refresh per active session every 5 minutes.
