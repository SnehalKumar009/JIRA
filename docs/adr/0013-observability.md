# ADR-0013: Observability — JSON logs, Micrometer, optional profile

- **Status:** Accepted
- **Date:** 2026-10-28

## Context
Production-grade requires logs, metrics, and traces; there is no uptime target and memory is limited to 3 GB.

## Decision
- Structured JSON logs to stdout (Spring Boot structured logging) with request and trace IDs.
- Metrics via Micrometer, exposed for Prometheus scraping; tracing via Micrometer Tracing with an OTLP exporter.
- Health and readiness endpoints via Spring Boot Actuator.
- Prometheus, Jaeger (all-in-one, in-memory), and Grafana run only under an optional `observability` Compose profile, with dashboards provisioned as code.
- No alerting (no uptime target).

## Alternatives considered
- **OpenTelemetry Java agent + Collector** — zero-code, but extra memory and startup cost.
- **Always-on observability stack** — doesn't fit the memory budget.

## Consequences
- Telemetry is always emitted; the stack to view it is started on demand.
- Traces are not kept across restarts; acceptable for debugging use.
- Log aggregation is deferred; JSON on stdout feeds any future pipeline.
