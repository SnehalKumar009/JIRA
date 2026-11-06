package dev.tracker.identity.token;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;

/**
 * Principal for a request authenticated with a personal access token. Its name is the user's IdP
 * subject — the same as a session principal — so per-user limits apply across both.
 */
public record TokenPrincipal(
        UUID tokenId,
        UUID userId,
        String subject,
        String username,
        String displayName,
        @Nullable String email,
        Set<String> scopes,
        boolean admin,
        Collection<? extends GrantedAuthority> authorities) implements OAuth2AuthenticatedPrincipal {

    @Override
    public Map<String, Object> getAttributes() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("token_id", tokenId.toString());
        attributes.put("user_id", userId.toString());
        attributes.put("sub", subject);
        attributes.put("scope", String.join(" ", scopes));
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        return subject;
    }
}
