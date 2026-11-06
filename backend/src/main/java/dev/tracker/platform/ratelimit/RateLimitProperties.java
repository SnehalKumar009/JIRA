package dev.tracker.platform.ratelimit;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Requests per minute, per signed-in user or per client IP for anonymous requests. */
@Validated
@ConfigurationProperties("tracker.rate-limit")
record RateLimitProperties(@Positive int authenticatedPerMinute, @Positive int anonymousPerMinute) {}
