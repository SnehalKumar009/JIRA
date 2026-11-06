# ADR-0021: Mantine UI and a nonce-based style policy

- **Status:** Accepted
- **Date:** 2026-11-06

## Context
The SPA needs a component library. Our Content Security Policy forbids inline scripts and styles.
Mantine was chosen because its component styles ship as static CSS — but it also renders a small
`<style>` element at runtime for its CSS variables, which a plain `style-src 'self'` blocks.

## Decision
- **Mantine** (core + hooks) for components, with TanStack Query for server state and React Router
  for routing.
- **Per-request CSP nonce for styles:**
  - a servlet filter generates a random nonce per request;
  - the CSP header carries `style-src 'self' 'nonce-…'`; scripts stay `script-src 'self'`;
  - the backend serves `index.html` for every SPA route with the nonce written into a
    `<meta name="csp-nonce">` tag; the SPA hands it to Mantine (`getStyleNonce`), which stamps it on
    the style elements it creates.
- `index.html` is never cached (`Cache-Control: no-store`), since every copy carries its own nonce.
- `form-action` also allows the Keycloak origin, because sign-out ends in a redirect to Keycloak.

## Alternatives considered
- **`'unsafe-inline'` for styles** — gives up the protection for every injected style.
- **Mantine without its CSS-variable element** (`withCssVariables={false}`) — means generating and
  maintaining the variable stylesheet ourselves.
- **Libraries with CSS-in-JS runtimes** (MUI, Chakra) — inject far more style elements.

## Consequences
- Strict CSP kept; verified in a real browser with no CSP violations.
- SPA routes must be listed in the backend's `SpaController` so they get the templated page.
- The Vite dev server has no CSP; the placeholder nonce there is harmless.
