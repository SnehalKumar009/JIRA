package dev.tracker.audit;

/**
 * Records audit events. Writes join the caller's transaction, so an action and its audit record
 * commit or roll back together.
 */
public interface AuditLog {

    void record(AuditRecord record);
}
