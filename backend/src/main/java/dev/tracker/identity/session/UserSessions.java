package dev.tracker.identity.session;

import static dev.tracker.jooq.Tables.OIDC_SESSION;

import org.jooq.DSLContext;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionRegistry;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/** Ends app sessions held in the JDBC session store. */
@Component
public class UserSessions {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final OidcSessionRegistry registry;
    private final DSLContext dsl;

    UserSessions(FindByIndexNameSessionRepository<? extends Session> sessions, OidcSessionRegistry registry,
            DSLContext dsl) {
        this.sessions = sessions;
        this.registry = registry;
        this.dsl = dsl;
    }

    /** Every session of the user, found through the principal-name index (the IdP subject). */
    public int endAll(String subject) {
        var ids = sessions.findByPrincipalName(subject).keySet();
        ids.forEach(sessions::deleteById);
        dsl.deleteFrom(OIDC_SESSION).where(OIDC_SESSION.SUBJECT.eq(subject)).execute();
        return ids.size();
    }

    public void end(String sessionId) {
        sessions.deleteById(sessionId);
        registry.removeSessionInformation(sessionId);
    }
}
