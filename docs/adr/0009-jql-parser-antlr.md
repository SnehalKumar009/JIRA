# ADR-0009: JQL parser built with ANTLR4

- **Status:** Accepted
- **Date:** 2026-10-28

## Context
Decision 3 requires a real JQL parser (subset). Errors must report positions; queries must compile to parameterised SQL with permission filtering injected.

## Decision
- JQL grammar defined in ANTLR4; generated parser produces a parse tree, converted into our own typed AST.
- AST → validation against the field registry → jOOQ conditions → permission filter added → bounded, paginated query.
- Error listener translates ANTLR errors into user-facing messages with column positions.

## Alternatives considered
- **Hand-written recursive-descent parser** — no dependency and full control of messages, but more code to get right and maintain.

## Consequences
- The grammar file is the specification of supported JQL.
- Generated code is a build step; our AST isolates the rest of the system from ANTLR types.
