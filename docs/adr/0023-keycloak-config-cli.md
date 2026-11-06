# ADR-0023: Keycloak realm as code with keycloak-config-cli

- **Status:** Accepted
- **Date:** 2026-11-06
- **Refines:** ADR-0002

## Context
The realm (client, policies, groups) must be code. Keycloak's native import only creates a realm
that doesn't exist yet; later edits would need manual steps or wiping the realm and its users.

## Decision
- `infra/keycloak/realm/tracker.json` is the realm definition. A one-shot `keycloak-config` Compose
  service (adorsys keycloak-config-cli) applies it on every start, idempotently; the app starts only
  after it succeeds.
- Environment-specific values (client secret, public URL, back-channel URL) are `$(env:…)`
  placeholders filled from `.env`.
- Users are not in the file — they come from people (and later directories). Only realm structure:
  the `tracker-admins` group, policies, the `tracker-app` client and its groups mapper.
- The file is self-sufficient for Keycloak's native import too (no custom client scopes): the
  integration tests import it into a Testcontainers Keycloak after filling in the placeholders.
- Policies in the file: TOTP enrolment required for every user, brute-force lockout, 12-character
  passwords, 5-minute access tokens, 30-minute idle / 10-hour maximum SSO sessions, login and admin
  events recorded.

## Alternatives considered
- **Native first-boot import** — later changes don't apply.
- **Terraform Keycloak provider** — another tool and state file to run on the desktop.

## Consequences
- The newest keycloak-config-cli build targets Keycloak 26.5; it works against our 26.8 (verified).
  Renovate tracks it; re-verify on Keycloak upgrades.
- The bootstrap admin account is used to apply config — acceptable on the desktop; a dedicated
  service account is the production step.
