package dev.tracker.identity.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * After the app session ends, the browser must also visit Keycloak's end-session endpoint
 * (RP-initiated logout). The SPA signs out with fetch — its CSRF token travels in a header — and
 * can't follow a cross-origin redirect, so a JSON request gets the URL to navigate to instead.
 */
class SpaLogoutSuccessHandler extends OidcClientInitiatedLogoutSuccessHandler {

    private final JsonMapper json;

    SpaLogoutSuccessHandler(ClientRegistrationRepository clients, JsonMapper json) {
        super(clients);
        this.json = json;
        setPostLogoutRedirectUri("{baseUrl}/");
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
            @Nullable Authentication authentication) throws IOException, ServletException {
        String accept = request.getHeader("Accept");
        if (accept == null || !accept.contains(MediaType.APPLICATION_JSON_VALUE)) {
            super.onLogoutSuccess(request, response, authentication);
            return;
        }
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getOutputStream().write(json.writeValueAsBytes(
                Map.of("redirectUrl", determineTargetUrl(request, response, authentication))));
    }
}
