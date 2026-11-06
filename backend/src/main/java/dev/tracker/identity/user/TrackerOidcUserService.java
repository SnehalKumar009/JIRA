package dev.tracker.identity.user;

import dev.tracker.identity.config.OidcProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * Turns a validated ID token into the session principal: syncs the user and groups, refuses
 * deactivated users, and derives authorities from IdP groups.
 */
@Component
public class TrackerOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    public static final String ACCOUNT_DISABLED = "account_disabled";

    // No user-info endpoint is configured, so this only validates and wraps the ID token.
    private final OidcUserService delegate = new OidcUserService();
    private final UserSyncService sync;
    private final OidcProperties oidc;

    TrackerOidcUserService(UserSyncService sync, OidcProperties oidc) {
        this.sync = sync;
        this.oidc = oidc;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        OidcUser oidcUser = delegate.loadUser(request);
        LoginIdentity identity = LoginIdentity.from(oidcUser);
        UserSyncService.Synced user = sync.syncOnLogin(identity);
        if (!user.active()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(ACCOUNT_DISABLED, "This account has been deactivated", null));
        }
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (identity.groups().contains(oidc.adminGroup())) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return new TrackerOidcUser(user.userId(), authorities, oidcUser.getIdToken());
    }
}
