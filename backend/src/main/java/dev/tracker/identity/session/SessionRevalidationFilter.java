package dev.tracker.identity.session;

import dev.tracker.audit.AuditActor;
import dev.tracker.audit.AuditLog;
import dev.tracker.audit.AuditRecord;
import dev.tracker.identity.user.ActiveUsers;
import dev.tracker.identity.user.TrackerOidcUser;
import dev.tracker.platform.ProblemResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Re-checks a browser session on every request:
 * <ul>
 *   <li>the user is still active here (deactivation takes effect at once), and
 *   <li>the Keycloak session is still alive: when the access token expires (every few minutes) it
 *       is refreshed, and a refused refresh — user disabled or logged out in Keycloak — ends the
 *       session. This bounds staleness even if a back-channel logout never arrives.
 * </ul>
 * Not a Spring bean: it is placed in the security filter chain only.
 */
public class SessionRevalidationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SessionRevalidationFilter.class);

    private final ActiveUsers activeUsers;
    private final OAuth2AuthorizedClientManager authorizedClients;
    private final UserSessions sessions;
    private final AuditLog audit;
    private final ProblemResponses problems;

    public SessionRevalidationFilter(ActiveUsers activeUsers, OAuth2AuthorizedClientManager authorizedClients,
            UserSessions sessions, AuditLog audit, ProblemResponses problems) {
        this.activeUsers = activeUsers;
        this.authorizedClients = authorizedClients;
        this.sessions = sessions;
        this.audit = audit;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof OAuth2AuthenticationToken token && token.getPrincipal() instanceof TrackerOidcUser user) {
            String reason = !activeUsers.isActive(user.getUserId()) ? "account_disabled"
                    : !idpSessionAlive(token, request, response) ? "idp_session_ended"
                    : null;
            if (reason != null) {
                end(request, user, reason);
                if (request.getRequestURI().startsWith("/api/")) {
                    problems.write(response, HttpStatus.UNAUTHORIZED, "Your session has ended. Sign in again.");
                    return;
                }
                // Pages and assets are public; continue anonymously and let the SPA show sign-in.
            }
        }
        chain.doFilter(request, response);
    }

    /** Returns the current token, refreshing it first if it has expired. */
    private boolean idpSessionAlive(OAuth2AuthenticationToken token, HttpServletRequest request,
            HttpServletResponse response) {
        OAuth2AuthorizeRequest authorize = OAuth2AuthorizeRequest
                .withClientRegistrationId(token.getAuthorizedClientRegistrationId())
                .principal(token)
                .attribute(HttpServletRequest.class.getName(), request)
                .attribute(HttpServletResponse.class.getName(), response)
                .build();
        try {
            return authorizedClients.authorize(authorize) != null;
        } catch (OAuth2AuthorizationException e) {
            log.info("Keycloak refused to refresh the session token: {}", e.getError().getErrorCode());
            return false;
        }
    }

    private void end(HttpServletRequest request, TrackerOidcUser user, String reason) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            sessions.end(session.getId());
        }
        SecurityContextHolder.clearContext();
        audit.record(AuditRecord.success(AuditActor.system(), "auth.session.end")
                .target("user", user.getUserId())
                .details(Map.of("reason", reason)));
    }
}
