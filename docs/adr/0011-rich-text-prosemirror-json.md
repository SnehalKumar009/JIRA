# ADR-0011: Rich text stored as ProseMirror JSON

- **Status:** Accepted
- **Date:** 2026-10-28

## Context
Descriptions and comments are rich text. Storing HTML invites stored XSS. Atlassian's own document format is ProseMirror-based.

## Decision
- Rich text is stored as ProseMirror JSON documents, edited with Tiptap.
- The server validates every document against an allow-listed schema (nodes, marks, attributes, link protocols) before saving.
- A plain-text projection is stored alongside for search and notifications.

## Alternatives considered
- **HTML + sanitiser** — sanitiser bypasses are a recurring vulnerability class.
- **Markdown** — simpler, but weaker fit for mentions, panels, and future structured nodes.

## Consequences
- XSS is prevented by construction rather than by filtering.
- Server needs a schema validator kept in sync with the editor configuration.
