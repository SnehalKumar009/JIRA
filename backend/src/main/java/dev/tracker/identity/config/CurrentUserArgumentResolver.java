package dev.tracker.identity.config;

import dev.tracker.identity.AuthMethod;
import dev.tracker.identity.CurrentUser;
import dev.tracker.identity.token.TokenPrincipal;
import dev.tracker.identity.user.TrackerOidcUser;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Lets controllers take a {@link CurrentUser} parameter, whatever the authentication method. */
@Configuration(proxyBeanMethods = false)
class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver, WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(this);
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == CurrentUser.class;
    }

    @Override
    public CurrentUser resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof OAuth2AuthenticationToken session && session.getPrincipal() instanceof TrackerOidcUser user) {
            return new CurrentUser(user.getUserId(), user.getSubject(), user.getUsername(), user.getDisplayName(),
                    user.getEmail(), isAdmin(auth), AuthMethod.SESSION, null);
        }
        if (auth instanceof BearerTokenAuthentication bearer && bearer.getPrincipal() instanceof TokenPrincipal token) {
            return new CurrentUser(token.userId(), token.subject(), token.username(), token.displayName(),
                    token.email(), token.admin(), AuthMethod.TOKEN, token.tokenId());
        }
        throw new AuthenticationCredentialsNotFoundException("No authenticated user");
    }

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("ROLE_ADMIN"::equals);
    }
}
