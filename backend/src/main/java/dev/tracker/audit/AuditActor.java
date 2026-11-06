package dev.tracker.audit;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Who performed an audited action. IDs only — names are resolved at read time (D8). */
public record AuditActor(Type type, @Nullable UUID userId, @Nullable UUID tokenId) {

    public enum Type { USER, TOKEN, SYSTEM, ANONYMOUS }

    public static AuditActor user(UUID userId) {
        return new AuditActor(Type.USER, userId, null);
    }

    public static AuditActor token(UUID userId, UUID tokenId) {
        return new AuditActor(Type.TOKEN, userId, tokenId);
    }

    public static AuditActor system() {
        return new AuditActor(Type.SYSTEM, null, null);
    }

    public static AuditActor anonymous() {
        return new AuditActor(Type.ANONYMOUS, null, null);
    }
}
