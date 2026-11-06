package dev.tracker.identity;

import dev.tracker.audit.AuditActor;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The authenticated caller. Controllers declare it as a parameter; it is resolved from the
 * security context for both session and token authentication.
 */
public record CurrentUser(
        UUID userId,
        String subject,
        String username,
        String displayName,
        @Nullable String email,
        boolean admin,
        AuthMethod authMethod,
        @Nullable UUID tokenId) {

    public AuditActor auditActor() {
        return tokenId != null ? AuditActor.token(userId, tokenId) : AuditActor.user(userId);
    }
}
