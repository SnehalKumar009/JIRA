package dev.tracker.identity.user;

import dev.tracker.audit.AuditLog;
import dev.tracker.audit.AuditRecord;
import dev.tracker.identity.CurrentUser;
import dev.tracker.identity.session.UserSessions;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Deactivation is the only way a user stops existing (D10): sign-in is refused, open sessions end
 * and access tokens stop working, while everything the user did stays attributed to them.
 */
@Service
class UserAdminService {

    private final UserRepository users;
    private final ActiveUsers activeUsers;
    private final UserSessions sessions;
    private final AuditLog audit;

    UserAdminService(UserRepository users, ActiveUsers activeUsers, UserSessions sessions, AuditLog audit) {
        this.users = users;
        this.activeUsers = activeUsers;
        this.sessions = sessions;
        this.audit = audit;
    }

    @Transactional
    void deactivate(CurrentUser admin, UUID userId) {
        if (admin.userId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot deactivate yourself");
        }
        String subject = subjectOrNotFound(userId);
        if (users.setActive(userId, false)) {
            audit.record(AuditRecord.success(admin.auditActor(), "user.deactivate").target("user", userId));
        }
        activeUsers.invalidate(userId);
        // Tokens need no action: they are refused for inactive users at every use.
        sessions.endAll(subject);
    }

    @Transactional
    void reactivate(CurrentUser admin, UUID userId) {
        subjectOrNotFound(userId);
        if (users.setActive(userId, true)) {
            audit.record(AuditRecord.success(admin.auditActor(), "user.reactivate").target("user", userId));
        }
        activeUsers.invalidate(userId);
    }

    private String subjectOrNotFound(UUID userId) {
        return users.subjectOf(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such user"));
    }
}
