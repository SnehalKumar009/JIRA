/**
 * Audit module: an append-only record of security-relevant actions (DOMAIN_MODEL §3.6). Other
 * modules write through {@link dev.tracker.audit.AuditLog}; admins read through the REST API.
 */
@NullMarked
package dev.tracker.audit;

import org.jspecify.annotations.NullMarked;
