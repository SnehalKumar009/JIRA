package dev.tracker.identity.config;

import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

/**
 * The Keycloak client registration, declared explicitly instead of discovered (ADR-0020): the
 * browser is sent to the public issuer URL, while the app's own calls (token, keys) go straight to
 * Keycloak on the internal network. Tokens still carry, and are checked against, the public issuer.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OidcProperties.class)
class OidcClientConfig {

    static final String REGISTRATION_ID = "keycloak";

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(OidcProperties oidc) {
        String browser = oidc.issuer() + "/protocol/openid-connect";
        String internal = oidc.backchannelUrl() + "/protocol/openid-connect";
        ClientRegistration keycloak = ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .clientId(oidc.clientId())
                .clientSecret(oidc.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .issuerUri(oidc.issuer())
                .authorizationUri(browser + "/auth")
                .tokenUri(internal + "/token")
                .jwkSetUri(internal + "/certs")
                // No user-info URI: everything needed is in the ID token, one fewer call per login.
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .providerConfigurationMetadata(Map.of("end_session_endpoint", browser + "/logout"))
                .clientName("Keycloak")
                .build();
        return new InMemoryClientRegistrationRepository(keycloak);
    }

    @Bean
    OAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new HttpSessionOAuth2AuthorizedClientRepository();
    }

    /** Refreshes expired access tokens; used by the session revalidation filter. */
    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository clients,
            OAuth2AuthorizedClientRepository authorizedClients) {
        DefaultOAuth2AuthorizedClientManager manager = new DefaultOAuth2AuthorizedClientManager(clients, authorizedClients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .refreshToken()
                .build());
        return manager;
    }
}
