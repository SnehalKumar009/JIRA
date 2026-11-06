/**
 * Identity module (FEATURES §3.1): who the caller is. Keycloak authenticates (OIDC, BFF sessions);
 * this module keeps the app's user records and groups, personal access tokens, and the HTTP
 * security configuration. Other modules use {@link dev.tracker.identity.CurrentUser}.
 */
@NullMarked
package dev.tracker.identity;

import org.jspecify.annotations.NullMarked;
