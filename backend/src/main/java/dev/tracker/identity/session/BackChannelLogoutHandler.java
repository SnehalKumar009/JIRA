package dev.tracker.identity.session;

import dev.tracker.audit.AuditActor;
import dev.tracker.audit.AuditLog;
import dev.tracker.audit.AuditRecord;
import dev.tracker.identity.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.oidc.authentication.logout.OidcLogoutToken;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionInformation;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionRegistry;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

/**
 * Handles a validated OIDC back-channel logout token from Keycloak by deleting the matching sessions
 * from the session store directly. (Spring's default handler instead calls the app's own /logout
 * over HTTP with each session's cookie, which doesn't fit Spring Session's cookie format.)
 */
@Component
public class BackChannelLogoutHandler implements LogoutHandler {

    private final OidcSessionRegistry registry;
    private final UserSessions sessions;
    private final UserRepository users;
    private final AuditLog audit;

    BackChannelLogoutHandler(OidcSessionRegistry registry, UserSessions sessions, UserRepository users,
            AuditLog audit) {
        this.registry = registry;
        this.sessions = sessions;
        this.users = users;
        this.audit = audit;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, @Nullable Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OidcLogoutToken token)) {
            return;
        }
        int ended = 0;
        for (OidcSessionInformation session : registry.removeSessionInformation(token)) {
            sessions.end(session.getSessionId());
            ended++;
        }
        AuditRecord record = AuditRecord.success(AuditActor.system(), "auth.logout.backchannel")
                .details(Map.of("sessionsEnded", ended));
        audit.record(users.idOfSubject(token.getSubject())
                .map(userId -> record.target("user", userId))
                .orElse(record));
    }
}
