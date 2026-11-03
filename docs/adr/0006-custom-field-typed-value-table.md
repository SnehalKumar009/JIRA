# ADR-0006: Custom-field values in a typed value table

- **Status:** Accepted
- **Date:** 2026-10-28

## Context
DOMAIN_MODEL D6 deferred the physical layout of custom-field values. Values must be queryable from JQL with typed comparisons, reference users and select options safely, and survive option renames and user anonymisation.

## Decision
A `custom_field_value` table: one row per issue per field per value (multi-value fields have several rows), with typed columns — `text_value`, `number_value`, `date_value`, `user_id` (FK), `option_id` (FK). A check constraint ensures exactly the column matching the field's type is set. Indexed per type column with the field ID.

This mirrors Jira's own `customfieldvalue` table.

## Alternatives considered
- **JSONB column on the issue** — simpler writes, GIN indexing, but no foreign keys to users / options, and typed range comparisons get awkward.
- **One real column per custom field** — schema changes at runtime; unacceptable.

## Consequences
- Referential integrity for user and option values; renames and anonymisation need no data rewrites.
- JQL on custom fields uses `EXISTS` subqueries; needs index care.
- System fields stay as real columns on `issue` (D6).
