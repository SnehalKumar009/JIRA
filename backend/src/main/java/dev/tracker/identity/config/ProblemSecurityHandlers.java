package dev.tracker.identity.config;

import dev.tracker.platform.ProblemResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 401 and 403 for the API as problem details, like every other API error. */
@Component
class ProblemSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ProblemResponses problems;

    ProblemSecurityHandlers(ProblemResponses problems) {
        this.problems = problems;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        if (e instanceof InvalidBearerTokenException) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
            problems.write(response, HttpStatus.UNAUTHORIZED, "The access token is invalid, expired or revoked.");
            return;
        }
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        problems.write(response, HttpStatus.UNAUTHORIZED, "Sign in or send a valid access token.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        problems.write(response, HttpStatus.FORBIDDEN, "You are not allowed to do this.");
    }
}
