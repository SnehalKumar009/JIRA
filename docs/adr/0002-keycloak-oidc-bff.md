# ADR-0002: Keycloak + OIDC with the BFF session pattern

- **Status:** Accepted
- **Date:** 2026-10-28

## Context
Production-grade means not hand-rolling authentication. We want MFA, single sign-on, and a path to AD / Entra ID / Okta without app changes. Identity records and authorization must stay in our app (FEATURES §3.1).

## Decision
- Keycloak is the identity provider, in production mode (`kc.sh start`), backed by Postgres, realm configuration as code imported on startup.
- The app is an OIDC confidential client using Authorization Code + PKCE.
- **Backend-for-frontend:** tokens never reach the browser; the browser holds only an `HttpOnly; Secure; SameSite` session cookie.
- OIDC back-channel logout ends app sessions when a user is disabled or logged out in Keycloak.
- Users created on first login keyed on `sub`; groups synced from the token. SCIM in P1.
- Personal access tokens are issued by our app (hashed, scoped, expiring), not by Keycloak.

## Alternatives considered
- **Internal user directory** — we would own password storage, resets, MFA.
- **Direct cloud IdP** — needs a tenant; not self-contained on a desktop.
- **SPA holding tokens** — tokens in browser storage are exposed to XSS.

## Consequences
- Keycloak is the heaviest component after the app (ADR-0016).
- Issuer URL must be identical from browser and containers (ADR-0012).
- Enterprise directories become Keycloak configuration, not code.
