package dev.tracker.platform.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.tracker.platform.ProblemResponses;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Token-bucket rate limiting (Bucket4j). Runs after the security filter chain, so signed-in callers
 * are limited per user — sessions and access tokens of one user share a bucket — and anonymous
 * callers per IP. Buckets live in memory: one app instance (ADR-0001).
 */
@Component
@EnableConfigurationProperties(RateLimitProperties.class)
class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties limits;
    private final ProblemResponses problems;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(10_000)
            .build();

    RateLimitFilter(RateLimitProperties limits, ProblemResponses problems) {
        this.limits = limits;
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/assets/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean signedIn = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
        String key = signedIn ? "user:" + auth.getName() : "ip:" + request.getRemoteAddr();
        int perMinute = signedIn ? limits.authenticatedPerMinute() : limits.anonymousPerMinute();

        Bucket bucket = buckets.get(key, k -> newBucket(perMinute));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfter = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
        response.setHeader("Retry-After", Long.toString(retryAfter));
        problems.write(response, HttpStatus.TOO_MANY_REQUESTS, "Too many requests; retry after " + retryAfter + "s.");
    }

    private static Bucket newBucket(int perMinute) {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)))
                .build();
    }
}
