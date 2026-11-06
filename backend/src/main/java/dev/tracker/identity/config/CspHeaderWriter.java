package dev.tracker.identity.config;

import dev.tracker.platform.CspNonce;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import org.springframework.security.web.header.HeaderWriter;

/**
 * Content Security Policy with a per-request style nonce (ADR-0021). Scripts only from our origin;
 * styles from our origin plus elements carrying this request's nonce. Forms may also target the
 * Keycloak origin, because sign-out posts to /logout, which redirects to Keycloak.
 */
class CspHeaderWriter implements HeaderWriter {

    private static final String HEADER = "Content-Security-Policy";

    private final String identityProviderOrigin;

    CspHeaderWriter(String issuer) {
        URI uri = URI.create(issuer);
        this.identityProviderOrigin = uri.getScheme() + "://" + uri.getAuthority();
    }

    @Override
    public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
        if (response.containsHeader(HEADER)) {
            return;
        }
        String nonce = CspNonce.of(request);
        String styleSrc = nonce != null ? "style-src 'self' 'nonce-" + nonce + "'" : "style-src 'self'";
        response.setHeader(HEADER, String.join("; ",
                "default-src 'self'",
                "script-src 'self'",
                styleSrc,
                "img-src 'self' data:",
                "font-src 'self'",
                "connect-src 'self'",
                "object-src 'none'",
                "base-uri 'self'",
                "form-action 'self' " + identityProviderOrigin,
                "frame-ancestors 'none'"));
    }
}
