package dev.tracker.identity.config;

import java.util.Set;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/** Request-level rules that depend on how the caller authenticated. */
final class Access {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private Access() {}

    /** Access tokens need {@code read} for safe methods and {@code write} for the rest; sessions pass. */
    static AuthorizationManager<RequestAuthorizationContext> tokenScopes() {
        return (Supplier<? extends @Nullable Authentication> authentication, RequestAuthorizationContext context) -> {
            if (!(authentication.get() instanceof BearerTokenAuthentication token)) {
                return granted(true);
            }
            String needed = SAFE_METHODS.contains(context.getRequest().getMethod()) ? "SCOPE_read" : "SCOPE_write";
            return granted(token.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(needed::equals));
        };
    }

    /** Only a signed-in browser session — used for endpoints that manage credentials. */
    static AuthorizationManager<RequestAuthorizationContext> sessionOnly() {
        return (Supplier<? extends @Nullable Authentication> authentication, RequestAuthorizationContext context) ->
                granted(authentication.get() instanceof OAuth2AuthenticationToken token && token.isAuthenticated());
    }

    private static AuthorizationResult granted(boolean granted) {
        return new AuthorizationDecision(granted);
    }
}
