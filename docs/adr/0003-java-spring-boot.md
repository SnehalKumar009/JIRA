# ADR-0003: Java + Spring Boot for the backend

- **Status:** Accepted — Java version amended by [ADR-0017](0017-java-21.md)
- **Date:** 2026-10-28

## Context
The backend carries a rich, strictly transactional domain (workflow engine, field registry, permission resolution, JQL compiler) and must meet production-grade security and testing standards. Jira itself is built on Java.

## Decision
Java (current LTS) with Spring Boot. Key pieces:
- Spring Security OAuth2 Client for the OIDC BFF flow and back-channel logout
- Spring Session JDBC for sessions (ADR-0008)
- Spring Modulith for module boundary verification (ADR-0014)
- jOOQ + Flyway for data (ADR-0005), ANTLR4 for JQL (ADR-0009)
- springdoc-openapi for the OpenAPI spec
- Bucket4j for rate limiting

## Alternatives considered
- **Go** — lean and fast, but more hand-built pieces (BFF flow, type-safe dynamic SQL).
- **TypeScript / NestJS** — one language end to end; weaker fit for a strict transactional domain.
- **Python / FastAPI** — less static safety across a large domain model.

## Consequences
- Mature, well-documented building blocks for every requirement; the ecosystem mirrors Jira's.
- Higher memory and startup cost than Go — managed by ADR-0016.
- Spring's conventions take time to learn; we prefer explicit configuration over hidden auto-magic where it matters (security, transactions).
