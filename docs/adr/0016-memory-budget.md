# ADR-0016: Whole stack under 3 GB RAM

- **Status:** Accepted
- **Date:** 2026-10-28

## Context
The stack runs on the developer desktop with less than 3 GB available. Two JVMs (app and Keycloak) dominate memory.

## Decision
- Every Compose service has a `mem_limit`:

  | Service | Limit |
  |---|---|
  | PostgreSQL | 256 MB |
  | Keycloak | 768 MB |
  | app (API + BFF + worker) | 768 MB |
  | Caddy | 64 MB |
  | Prometheus / Jaeger (optional) | 192 MB each |
  | Grafana (optional) | 256 MB |

  Core limits ≈ 1.9 GB; with observability ≈ 2.4 GB.
- JVMs sized by percentage of container limit; Class Data Sharing for app startup; Keycloak built as an optimised image.
- Worker runs in the app JVM (ADR-0001); Micrometer instead of the OTel agent (ADR-0013).
- M0 exit criterion: measured usage within limits under the seeded dataset.

## Alternatives considered
- **Go backend** — lower memory, rejected for ecosystem reasons (ADR-0003).
- **GraalVM native image now** — large memory savings, but slower builds and reflection configuration; kept as a fallback.

## Consequences
- Fallback order if limits are exceeded: tighten JVM settings → drop Grafana → native image for the app.
- Limits make out-of-memory failures visible early instead of starving the desktop.

## Measured (M0, 2026-10-28)

`scripts/measure-memory.sh` with an idle stack:

| Service | Used | Limit |
|---|---|---|
| app | ~235 MiB | 768 MiB |
| Keycloak | ~470 MiB | 768 MiB |
| PostgreSQL | ~75 MiB | 256 MiB |
| Caddy | ~13 MiB | 64 MiB |
| **Core** | **~800 MiB** | |
| Grafana | ~167 MiB | 256 MiB (raised from 192 after measuring 86% use) |
| Prometheus | ~39 MiB | 192 MiB |
| Jaeger | ~12 MiB | 192 MiB |
| **With observability** | **~1.05 GiB** | |

Re-measure as features land; the fallbacks above have not been needed.
