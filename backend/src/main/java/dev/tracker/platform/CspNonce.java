package dev.tracker.platform;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;

/**
 * Per-request nonce for the Content Security Policy. Lets the SPA's UI library insert its own
 * {@code <style>} elements without allowing inline styles in general (ADR-0021).
 */
public final class CspNonce {

    static final String ATTRIBUTE = CspNonce.class.getName();

    private CspNonce() {}

    /** The nonce for this request, or {@code null} if the request did not pass the nonce filter. */
    public static @Nullable String of(HttpServletRequest request) {
        return (String) request.getAttribute(ATTRIBUTE);
    }
}
