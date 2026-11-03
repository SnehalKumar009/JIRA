# Runbook: observability profile

Logs are always JSON on stdout (`docker compose logs`). Metrics and traces are always emitted by
the app; the tools to look at them run only when you want them (ADR-0013).

## Start

```bash
TRACING_EXPORT_ENABLED=true docker compose --profile observability up -d --wait
```

Or set `TRACING_EXPORT_ENABLED=true` in `.env` while you use the profile — without it the app
keeps traces to itself.

| Tool | URL | Notes |
|---|---|---|
| Grafana | <http://localhost:3300> | User `admin`, password `GRAFANA_ADMIN_PASSWORD` from `.env`. Dashboard: Tracker → Tracker overview |
| Prometheus | <http://localhost:9090> | Scrapes the app (`/actuator/prometheus`) and Keycloak (`/metrics`) |
| Jaeger | <http://localhost:16686> | Service `tracker`. Traces are in memory only and vanish on restart |

Ports are configurable in `.env` (`GRAFANA_PORT`, `PROMETHEUS_PORT`, `JAEGER_UI_PORT`).

## Stop

```bash
docker compose --profile observability stop prometheus jaeger grafana
docker compose --profile observability rm -f prometheus jaeger grafana
```

Then set `TRACING_EXPORT_ENABLED=false` (or unset it) and `docker compose up -d --wait` so the app
stops exporting.

## Memory

With the profile running the stack uses about 1.1 GB (measured in M0), within the 3 GB budget
(ADR-0016). Check with `scripts/measure-memory.sh`.
