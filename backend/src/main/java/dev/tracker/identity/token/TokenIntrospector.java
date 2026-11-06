package dev.tracker.identity.token;

import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.stereotype.Component;

/**
 * Lets Spring Security's bearer-token support authenticate our personal access tokens: the
 * "introspection" is a lookup in our own database.
 */
@Component
public class TokenIntrospector implements OpaqueTokenIntrospector {

    private final TokenService tokens;

    TokenIntrospector(TokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        return tokens.authenticate(token)
                .orElseThrow(() -> new BadOpaqueTokenException("Token is invalid, expired or revoked"));
    }
}
