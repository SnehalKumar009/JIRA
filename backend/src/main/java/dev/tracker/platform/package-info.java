/**
 * Platform module: cross-cutting infrastructure shared by every other module — request
 * correlation, API error format, security headers, system information. Later milestones add the
 * transactional outbox and background worker here (ADR-0007).
 */
@NullMarked
package dev.tracker.platform;

import org.jspecify.annotations.NullMarked;
