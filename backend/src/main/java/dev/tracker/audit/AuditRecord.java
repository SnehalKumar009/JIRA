package dev.tracker.audit;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One audited action. {@code action} is a dotted, past-tense-free verb phrase such as
 * {@code auth.login} or {@code token.revoke}; {@code details} must not contain secrets.
 */
public record AuditRecord(
        AuditActor actor,
        String action,
        Outcome outcome,
        @Nullable String targetType,
        @Nullable String targetId,
        Map<String, Object> details) {

    public enum Outcome { SUCCESS, FAILURE }

    public static AuditRecord success(AuditActor actor, String action) {
        return new AuditRecord(actor, action, Outcome.SUCCESS, null, null, Map.of());
    }

    public static AuditRecord failure(AuditActor actor, String action) {
        return new AuditRecord(actor, action, Outcome.FAILURE, null, null, Map.of());
    }

    public AuditRecord target(String type, Object id) {
        return new AuditRecord(actor, action, outcome, type, id.toString(), details);
    }

    public AuditRecord details(Map<String, Object> details) {
        return new AuditRecord(actor, action, outcome, targetType, targetId, Map.copyOf(details));
    }
}
