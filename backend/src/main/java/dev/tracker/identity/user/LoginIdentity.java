package dev.tracker.identity.user;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/** What the identity provider tells us about a user at login, from the ID token. */
record LoginIdentity(String subject, String username, String displayName, @Nullable String email, Set<String> groups) {

    private static final int MAX_GROUP_NAME = 200;

    static LoginIdentity from(OidcUser user) {
        String username = user.getPreferredUsername() != null ? user.getPreferredUsername() : user.getSubject();
        String displayName = user.getFullName() != null ? user.getFullName() : username;
        return new LoginIdentity(user.getSubject(), username, displayName, user.getEmail(), groups(user));
    }

    private static Set<String> groups(OidcUser user) {
        // Keycloak's group mapper emits a JSON array of names (full.path=false).
        List<String> claim = user.getClaimAsStringList("groups");
        Collection<String> names = claim != null ? claim : List.of();
        return names.stream()
                .map(String::strip)
                .filter(name -> !name.isEmpty() && name.length() <= MAX_GROUP_NAME)
                .collect(Collectors.toUnmodifiableSet());
    }
}
