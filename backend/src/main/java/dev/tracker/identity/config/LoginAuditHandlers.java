package dev.tracker.identity.config;

import dev.tracker.audit.AuditActor;
import dev.tracker.audit.AuditLog;
import dev.tracker.audit.AuditRecord;
import dev.tracker.identity.user.TrackerOidcUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionRegistry;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

/** Audits interactive sign-in and sign-out; sends the browser back to the SPA either way. */
@Component
class LoginAuditHandlers {

    private static final Pattern SAFE_CODE = Pattern.compile("[a-z_]{1,40}");

    private final AuditLog audit;
    private final OidcSessionRegistry registry;

    LoginAuditHandlers(AuditLog audit, OidcSessionRegistry registry) {
        this.audit = audit;
        this.registry = registry;
    }

    AuthenticationSuccessHandler success() {
        SavedRequestAwareAuthenticationSuccessHandler redirect = new SavedRequestAwareAuthenticationSuccessHandler();
        redirect.setDefaultTargetUrl("/");
        return (request, response, authentication) -> {
            if (authentication.getPrincipal() instanceof TrackerOidcUser user) {
                audit.record(AuditRecord.success(AuditActor.user(user.getUserId()), "auth.login")
                        .target("user", user.getUserId()));
            }
            redirect.onAuthenticationSuccess(request, response, authentication);
        };
    }

    AuthenticationFailureHandler failure() {
        return (HttpServletRequest request, HttpServletResponse response, AuthenticationException e) -> {
            String code = e instanceof OAuth2AuthenticationException oauth ? oauth.getError().getErrorCode() : "failed";
            if (!SAFE_CODE.matcher(code).matches()) {
                code = "failed";
            }
            audit.record(AuditRecord.failure(AuditActor.anonymous(), "auth.login").details(Map.of("error", code)));
            response.sendRedirect("/?loginError=" + code);
        };
    }

    LogoutHandler logout() {
        return (HttpServletRequest request, HttpServletResponse response, @Nullable Authentication authentication) -> {
            HttpSession session = request.getSession(false);
            if (session != null) {
                registry.removeSessionInformation(session.getId());
            }
            if (authentication != null && authentication.getPrincipal() instanceof TrackerOidcUser user) {
                audit.record(AuditRecord.success(AuditActor.user(user.getUserId()), "auth.logout")
                        .target("user", user.getUserId()));
            }
        };
    }
}
