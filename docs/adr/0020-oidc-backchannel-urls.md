# ADR-0020: Internal URLs for the app's own Keycloak calls

- **Status:** Accepted
- **Date:** 2026-11-06
- **Amends:** ADR-0012 (the browser still uses one public URL; the app's server-to-server calls no longer do)

## Context
ADR-0012 routed every OIDC call through Caddy so the browser and the containers would use the same
issuer URL. That would make the app trust Caddy's local CA for its own calls to Keycloak (token
exchange, signing keys, refresh), which means distributing the CA into the app container — and the
CA directory also holds the CA's private key.

## Decision
- The Keycloak client registration is declared explicitly in code, not discovered:
  - **authorization and end-session endpoints** (the browser follows these): public issuer,
    `https://auth.test:8443/realms/tracker`;
  - **token and JWKS endpoints** (the app calls these): Keycloak directly on the internal network,
    `http://keycloak:8080/realms/tracker`.
- The issuer is pinned to the public URL; ID tokens and back-channel logout tokens must carry it.
  Keycloak's fixed hostname (`KC_HOSTNAME`) guarantees tokens carry it however they are requested.
- No user-info call: the ID token carries everything needed.
- Keycloak calls the app's back-channel logout endpoint internally (`http://app:8080/...`).

## Alternatives considered
- **Everything through Caddy** — needs the local CA in the app's trust store.
- **Discovery from the issuer** — would make the app fetch metadata through Caddy at startup.

## Consequences
- No CA distribution to the app; no dependency on Caddy for the app's own calls.
- Endpoint paths are hard-coded to Keycloak's layout; changing identity provider means changing
  this configuration (a deliberate, reviewed change).
