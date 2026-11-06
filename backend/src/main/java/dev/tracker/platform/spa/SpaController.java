package dev.tracker.platform.spa;

import dev.tracker.platform.CspNonce;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * Serves the SPA's index.html for every client-side route, with this request's CSP nonce filled
 * in. Hashed assets under /assets are served as plain static resources.
 */
@Controller
class SpaController {

    static final String NONCE_PLACEHOLDER = "__CSP_NONCE__";

    private final ClassPathResource index = new ClassPathResource("static/index.html");
    private volatile @Nullable String template;

    // Client-side routes. Keep in step with the router in frontend/src.
    @GetMapping({"/", "/index.html", "/settings/**"})
    ResponseEntity<String> index(HttpServletRequest request) {
        String nonce = Objects.requireNonNull(CspNonce.of(request), "CSP nonce filter did not run");
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore())
                .body(template().replace(NONCE_PLACEHOLDER, nonce));
    }

    private String template() {
        String cached = template;
        if (cached == null) {
            if (!index.exists()) {
                // Backend running without a frontend build (tests, spring-boot:test-run).
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Frontend is not bundled in this build");
            }
            try (InputStream in = index.getInputStream()) {
                cached = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read static/index.html", e);
            }
            template = cached;
        }
        return cached;
    }
}
