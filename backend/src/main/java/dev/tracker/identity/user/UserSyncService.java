package dev.tracker.identity.user;

import dev.tracker.audit.AuditActor;
import dev.tracker.audit.AuditLog;
import dev.tracker.audit.AuditRecord;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keeps the local user record and group memberships in step with the IdP, on every login. */
@Service
class UserSyncService {

    private final UserRepository users;
    private final AuditLog audit;

    UserSyncService(UserRepository users, AuditLog audit) {
        this.users = users;
        this.audit = audit;
    }

    record Synced(UUID userId, boolean active) {}

    @Transactional
    Synced syncOnLogin(LoginIdentity identity) {
        UserRepository.Upserted user = users.upsert(identity);
        if (user.created()) {
            audit.record(AuditRecord.success(AuditActor.system(), "user.create")
                    .target("user", user.id())
                    .details(Map.of("source", "first-login")));
        }
        if (!user.active()) {
            return new Synced(user.id(), false);
        }
        users.replaceGroups(user.id(), identity.groups());
        users.recordLogin(user.id());
        return new Synced(user.id(), true);
    }
}
