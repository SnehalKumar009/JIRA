# ADR-0012: Caddy with a local CA and `.test` hostnames

- **Status:** Accepted — port amended by [ADR-0019](0019-configurable-https-port.md); the app's own calls to Keycloak by [ADR-0020](0020-oidc-backchannel-urls.md)
- **Date:** 2026-10-28

## Context
Secure cookies and OIDC need HTTPS. Keycloak's token issuer URL must be identical whether reached from the browser or from the app container — `localhost` means different things in each.

## Decision
- Caddy terminates TLS using `tls internal` (its own local CA); the root certificate is trusted once on the desktop.
- Hostnames `tracker.test` and `auth.test` map to `127.0.0.1` in the desktop's `/etc/hosts`; inside Compose, the same names are network aliases of the Caddy service, so browser and containers resolve the same URLs.
- Keycloak runs behind the proxy with its hostname set explicitly to `https://auth.test`.

## Alternatives considered
- **Traefik / nginx** — capable, but more configuration for local certificates.
- **Plain HTTP on localhost** — breaks the production-grade cookie and issuer setup.

## Consequences
- One-time setup step on the desktop (hosts entries, trust Caddy's root CA), documented in the M0 runbook.
- Issuer mismatches — the most common Keycloak-in-Docker failure — are designed out.
