package dev.tracker.platform;

import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

/** The correlation ID of the request being handled — in logs, responses and audit events. */
public final class RequestId {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "request.id";

    private RequestId() {}

    public static @Nullable String current() {
        return MDC.get(MDC_KEY);
    }
}
