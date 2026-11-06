package dev.tracker.identity.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Keycloak client settings (ADR-0002, ADR-0020).
 *
 * @param issuer the realm's public issuer, as the browser sees it and as tokens carry it
 * @param backchannelUrl the same realm reached directly over the internal network, for token,
 *     key and logout calls from the app — no trip through the public proxy
 * @param adminGroup IdP group whose members are tracker administrators
 */
@Validated
@ConfigurationProperties("tracker.oidc")
public record OidcProperties(
        @NotBlank String issuer,
        @NotBlank String backchannelUrl,
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        @NotBlank String adminGroup) {}
