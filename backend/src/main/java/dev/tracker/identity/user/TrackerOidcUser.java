package dev.tracker.identity.user;

import java.io.Serial;
import java.util.Collection;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

/**
 * The session principal: the OIDC user plus our own user ID. Stored in the JDBC session through
 * Java serialization, so keep its fields few and stable (ADR-0008).
 */
public final class TrackerOidcUser extends DefaultOidcUser {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID userId;

    TrackerOidcUser(UUID userId, Collection<? extends GrantedAuthority> authorities, OidcIdToken idToken) {
        super(authorities, idToken, "sub");
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUsername() {
        String username = getPreferredUsername();
        return username != null ? username : getSubject();
    }

    public String getDisplayName() {
        String name = getFullName();
        return name != null ? name : getUsername();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TrackerOidcUser that && super.equals(that) && userId.equals(that.userId);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + userId.hashCode();
    }
}
