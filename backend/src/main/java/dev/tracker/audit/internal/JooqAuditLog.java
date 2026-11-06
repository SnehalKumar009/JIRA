package dev.tracker.audit.internal;

import static dev.tracker.jooq.Tables.AUDIT_EVENT;

import dev.tracker.audit.AuditLog;
import dev.tracker.audit.AuditRecord;
import dev.tracker.platform.RequestId;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

@Component
class JooqAuditLog implements AuditLog {

    private final DSLContext dsl;
    private final JsonMapper json;

    JooqAuditLog(DSLContext dsl, JsonMapper json) {
        this.dsl = dsl;
        this.json = json;
    }

    @Override
    public void record(AuditRecord record) {
        dsl.insertInto(AUDIT_EVENT)
                .set(AUDIT_EVENT.ACTOR_TYPE, record.actor().type().name().toLowerCase(Locale.ROOT))
                .set(AUDIT_EVENT.ACTOR_USER_ID, record.actor().userId())
                .set(AUDIT_EVENT.ACTOR_TOKEN_ID, record.actor().tokenId())
                .set(AUDIT_EVENT.ACTION, record.action())
                .set(AUDIT_EVENT.OUTCOME, record.outcome().name().toLowerCase(Locale.ROOT))
                .set(AUDIT_EVENT.TARGET_TYPE, record.targetType())
                .set(AUDIT_EVENT.TARGET_ID, record.targetId())
                .set(AUDIT_EVENT.DETAILS, JSONB.valueOf(json.writeValueAsString(record.details())))
                .set(AUDIT_EVENT.REQUEST_ID, RequestId.current())
                // inet has no jOOQ type: address the column as text and cast the value explicitly.
                .set(DSL.field(AUDIT_EVENT.CLIENT_IP.getUnqualifiedName(), String.class),
                        DSL.field("cast({0} as inet)", String.class, DSL.val(clientIp(), String.class)))
                .execute();
    }

    private static @Nullable String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
