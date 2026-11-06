package dev.tracker.identity.config;

import static org.springframework.security.authorization.AuthenticatedAuthorizationManager.authenticated;
import static org.springframework.security.authorization.AuthorityAuthorizationManager.hasRole;
import static org.springframework.security.authorization.AuthorizationManagers.allOf;

import dev.tracker.audit.AuditLog;
import dev.tracker.identity.session.BackChannelLogoutHandler;
import dev.tracker.identity.session.SessionRevalidationFilter;
import dev.tracker.identity.session.UserSessions;
import dev.tracker.identity.token.TokenIntrospector;
import dev.tracker.identity.user.ActiveUsers;
import dev.tracker.identity.user.TrackerOidcUserService;
import dev.tracker.platform.ProblemResponses;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import tools.jackson.databind.json.JsonMapper;

/**
 * One security filter chain for the whole app (ADR-0002):
 * <ul>
 *   <li>browsers sign in through Keycloak (OIDC authorization code + PKCE) and hold only a session
 *       cookie; tokens stay server-side (BFF);
 *   <li>scripts send a personal access token as a bearer token;
 *   <li>Keycloak ends sessions through OIDC back-channel logout.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    private static final String LOGIN_PATH = "/oauth2/authorization/" + OidcClientConfig.REGISTRATION_ID;

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            OidcProperties oidc,
            ClientRegistrationRepository clients,
            OAuth2AuthorizedClientManager authorizedClients,
            TrackerOidcUserService oidcUserService,
            TokenIntrospector tokenIntrospector,
            BackChannelLogoutHandler backChannelLogout,
            LoginAuditHandlers loginAudit,
            ProblemSecurityHandlers problemHandlers,
            ActiveUsers activeUsers,
            UserSessions sessions,
            AuditLog audit,
            ProblemResponses problems,
            JsonMapper json) throws Exception {

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/system/info", "/api/v1/openapi.json").permitAll()
                        .requestMatchers("/api/v1/me/tokens", "/api/v1/me/tokens/**").access(Access.sessionOnly())
                        .requestMatchers("/api/v1/admin/**").access(allOf(hasRole("ADMIN"), Access.tokenScopes()))
                        .requestMatchers("/api/**").access(allOf(authenticated(), Access.tokenScopes()))
                        // The SPA shell and its assets are public; the data behind them is not.
                        .anyRequest().permitAll())
                .oauth2Login(login -> login
                        .loginPage(LOGIN_PATH)
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService))
                        .successHandler(loginAudit.success())
                        .failureHandler(loginAudit.failure()))
                .oidcLogout(logout -> logout.backChannel(backChannel -> backChannel.logoutHandler(backChannelLogout)))
                .logout(logout -> logout
                        .addLogoutHandler(loginAudit.logout())
                        .logoutSuccessHandler(new SpaLogoutSuccessHandler(clients, json)))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .opaqueToken(opaque -> opaque.introspector(tokenIntrospector))
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers))
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(problemHandlers,
                                PathPatternRequestMatcher.withDefaults().matcher("/api/**"))
                        .defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint(LOGIN_PATH),
                                AnyRequestMatcher.INSTANCE)
                        .accessDeniedHandler(problemHandlers))
                .csrf(csrf -> csrf.spa())
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .addFilterBefore(new SessionRevalidationFilter(activeUsers, authorizedClients, sessions, audit, problems),
                        AuthorizationFilter.class)
                .headers(headers -> headers
                        .addHeaderWriter(new CspHeaderWriter(oidc.issuer()))
                        // Sent only on HTTPS requests; Caddy terminates TLS and forwards X-Forwarded-Proto.
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .addHeaderWriter(new StaticHeadersWriter(
                                "Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()")));
        return http.build();
    }

    /**
     * There are no local passwords — authentication is delegated to Keycloak. Declaring an empty
     * manager stops Spring Boot from generating a default user with a logged password.
     */
    @Bean
    UserDetailsService noLocalUsers() {
        return new InMemoryUserDetailsManager();
    }
}
