# ADR-0010: React + TypeScript + Vite SPA served same-origin

- **Status:** Accepted
- **Date:** 2026-10-28

## Context
The UI needs a rich issue view, drag-and-drop board, and JQL search. Authentication uses a BFF session cookie (ADR-0002).

## Decision
- React + TypeScript, built with Vite into static assets served by the app at the same origin as the API.
- TanStack Query for server state, React Router for routing, dnd-kit for drag-and-drop, Tiptap for rich text (ADR-0011).
- API client generated from the OpenAPI spec; CSRF protection on state-changing requests.
- Component library chosen in an M1 ADR.

## Alternatives considered
- **Next.js** — adds a second server; the backend is already the BFF.
- **Separate origin for the SPA** — forces CORS and complicates cookie security.

## Consequences
- No CORS, no tokens in the browser.
- Node.js is needed only at build time, not at runtime.
