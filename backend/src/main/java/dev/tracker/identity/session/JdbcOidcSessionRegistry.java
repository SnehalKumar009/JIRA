package dev.tracker.identity.session;

import static dev.tracker.jooq.Tables.OIDC_SESSION;

import dev.tracker.jooq.tables.records.OidcSessionRecord;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.oidc.authentication.logout.OidcLogoutToken;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionInformation;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionRegistry;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * Spring Security's link between app sessions and Keycloak sessions, kept in PostgreSQL instead
 * of memory so OIDC back-channel logout still works after the app restarts.
 */
@Component
class JdbcOidcSessionRegistry implements OidcSessionRegistry {

    private final DSLContext dsl;

    JdbcOidcSessionRegistry(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void saveSessionInformation(OidcSessionInformation info) {
        OidcUser user = info.getPrincipal();
        String issuer = user.getIssuer().toString();
        String sid = user.getClaimAsString("sid");
        dsl.insertInto(OIDC_SESSION)
                .set(OIDC_SESSION.SESSION_ID, info.getSessionId())
                .set(OIDC_SESSION.ISSUER, issuer)
                .set(OIDC_SESSION.SUBJECT, user.getSubject())
                .set(OIDC_SESSION.SID, sid)
                .onConflict(OIDC_SESSION.SESSION_ID)
                .doUpdate()
                .set(OIDC_SESSION.ISSUER, issuer)
                .set(OIDC_SESSION.SUBJECT, user.getSubject())
                .set(OIDC_SESSION.SID, sid)
                .execute();
    }

    @Override
    public @Nullable OidcSessionInformation removeSessionInformation(String clientSessionId) {
        return dsl.deleteFrom(OIDC_SESSION)
                .where(OIDC_SESSION.SESSION_ID.eq(clientSessionId))
                .returning()
                .fetchOptional()
                .map(JdbcOidcSessionRegistry::toInformation)
                .orElse(null);
    }

    /** A logout token names one Keycloak session ({@code sid}) or, failing that, all of a user's. */
    @Override
    public Iterable<OidcSessionInformation> removeSessionInformation(OidcLogoutToken token) {
        Condition match = OIDC_SESSION.ISSUER.eq(token.getIssuer().toString());
        match = token.getSessionId() != null
                ? match.and(OIDC_SESSION.SID.eq(token.getSessionId()))
                : match.and(OIDC_SESSION.SUBJECT.eq(token.getSubject()));
        List<OidcSessionInformation> removed = dsl.deleteFrom(OIDC_SESSION)
                .where(match)
                .returning()
                .fetch()
                .map(JdbcOidcSessionRegistry::toInformation);
        return removed;
    }

    // Only the session ID is used downstream; the principal is a minimal stand-in rebuilt from the row.
    private static OidcSessionInformation toInformation(OidcSessionRecord row) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(IdTokenClaimNames.ISS, row.getIssuer());
        claims.put(IdTokenClaimNames.SUB, row.getSubject());
        if (row.getSid() != null) {
            claims.put("sid", row.getSid());
        }
        Instant issued = row.getCreatedAt().toInstant();
        OidcIdToken idToken = new OidcIdToken("unavailable", issued, issued.plusSeconds(1), claims);
        DefaultOidcUser user = new DefaultOidcUser(AuthorityUtils.NO_AUTHORITIES, idToken, IdTokenClaimNames.SUB);
        return new OidcSessionInformation(row.getSessionId(), Map.of(), user);
    }
}
