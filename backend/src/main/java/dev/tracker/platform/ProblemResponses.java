package dev.tracker.platform;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes RFC 9457 problem details from filters, where controller advice can't reach — security
 * entry points, rate limiting. Same shape as the API exception handler produces.
 */
@Component
public class ProblemResponses {

    private final JsonMapper json;

    ProblemResponses(JsonMapper json) {
        this.json = json;
    }

    public void write(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        String requestId = RequestId.current();
        if (requestId != null) {
            body.put("requestId", requestId);
        }
        response.setStatus(status.value());
        // JSON is UTF-8 by definition (RFC 8259): write bytes, so no charset parameter is needed.
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getOutputStream().write(json.writeValueAsBytes(body));
    }
}
