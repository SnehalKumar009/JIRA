# ADR-0024: Personal access tokens

- **Status:** Accepted
- **Date:** 2026-11-06

## Context
Scripts and integrations can't do a browser sign-in. They need revocable, scoped, expiring
credentials that don't depend on the browser session.

## Decision
- Format `trk_pat_` + 43 characters (256 random bits, base64url). The prefix makes leaked tokens easy
  to spot; a gitleaks rule checks commits for it.
- Stored as SHA-256 only — tokens are high-entropy, so a fast hash is safe and lookup is direct.
  The value is shown once, at creation. A short display prefix identifies tokens in lists.
- Scopes: `read` (safe methods), `write` (implies read), `admin` (admin API; only admins can create
  it, and it only works while the owner is still an admin).
- Expiry required: default 90 days, maximum 365. At most 50 active tokens per user.
- A token works only if it is unrevoked, unexpired and its owner is active — checked on every use.
  Last use is recorded at most once a minute.
- Tokens are created and revoked from a **browser session only**; a token can't mint tokens.
- Authentication plugs into Spring Security's bearer-token support with our own "introspector" (a
  database lookup), so 401s carry the standard `WWW-Authenticate: Bearer error="invalid_token"`.

## Alternatives considered
- **Keycloak-issued tokens** (service accounts, offline tokens) — per-user scripted access would
  mean handing out Keycloak credentials; revocation and listing would live outside the app.
- **bcrypt/argon2 hashing** — designed for low-entropy passwords; needless cost here.

## Consequences
- Deactivating a user stops all their tokens at once without touching the token table.
- Creation and revocation are audited.
