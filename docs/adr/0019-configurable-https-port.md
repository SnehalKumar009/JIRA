# ADR-0019: Configurable HTTPS port, default 8443

- **Status:** Accepted
- **Date:** 2026-10-28
- **Amends:** ADR-0012 (hostnames and local CA unchanged; port only)

## Context
ADR-0012 assumed `https://tracker.test` and `https://auth.test` on port 443. On the development
desktop, MicroK8s' ingress controller intercepts ports 80 and 443 before Docker's port mappings,
so requests reached Kubernetes instead of Caddy.

## Decision
- HTTPS port set by `TRACKER_HTTPS_PORT` in `.env`, default **8443**.
- Caddy listens on that port *inside* its container too, and Keycloak's hostname includes it
  (`https://auth.test:8443`), so the browser and the containers use identical URLs and the OIDC
  issuer stays consistent.
- No plain-HTTP listener is published.

## Alternatives considered
- **Disable MicroK8s ingress** — breaks other local work.
- **Map 8443 on the host to 443 in the container** — the issuer URL would differ between browser
  and containers, the exact failure ADR-0012 designs out.

## Consequences
- URLs carry the port: `https://tracker.test:8443`.
- Changing the port after first start changes the Keycloak issuer URL; tokens and client
  configuration must follow.
- No privileged port is needed on the host.
