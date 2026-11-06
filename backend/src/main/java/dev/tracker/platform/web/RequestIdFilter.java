package dev.tracker.platform.web;

import dev.tracker.platform.RequestId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a correlation ID: taken from {@code X-Request-Id} when the caller sends a
 * well-formed one, generated otherwise. It is put in the logging MDC, echoed in the response, and
 * included in API error bodies.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter extends OncePerRequestFilter {

    // Restrictive so a caller can't inject log-forging characters through the header.
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(RequestId.HEADER);
        String requestId = incoming != null && VALID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();
        MDC.put(RequestId.MDC_KEY, requestId);
        response.setHeader(RequestId.HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(RequestId.MDC_KEY);
        }
    }
}
