package dev.tracker.audit.internal;

import static dev.tracker.jooq.Tables.AUDIT_EVENT;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Admin read access to the audit log, newest first, keyset-paginated by event ID. */
@RestController
@RequestMapping("/api/v1/admin/audit-events")
@Tag(name = "Admin")
@Validated
class AuditEventController {

    private static final TypeReference<Map<String, Object>> DETAILS = new TypeReference<>() {};

    private final DSLContext dsl;
    private final JsonMapper json;

    AuditEventController(DSLContext dsl, JsonMapper json) {
        this.dsl = dsl;
        this.json = json;
    }

    @GetMapping
    @Operation(summary = "List audit events, newest first")
    Page list(
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
            @RequestParam(required = false) @Nullable Long before,
            @RequestParam(required = false) @Nullable String action) {
        Condition condition = DSL.noCondition();
        if (before != null) {
            condition = condition.and(AUDIT_EVENT.ID.lt(before));
        }
        if (action != null) {
            condition = condition.and(AUDIT_EVENT.ACTION.eq(action));
        }
        List<Event> events = dsl.selectFrom(AUDIT_EVENT)
                .where(condition)
                .orderBy(AUDIT_EVENT.ID.desc())
                .limit(limit)
                .fetch(r -> new Event(
                        r.getId(), r.getOccurredAt(), r.getActorType(), r.getActorUserId(), r.getActorTokenId(),
                        r.getAction(), r.getOutcome(), r.getTargetType(), r.getTargetId(),
                        json.readValue(r.getDetails().data(), DETAILS), r.getRequestId()));
        Long next = events.size() == limit ? events.getLast().id() : null;
        return new Page(events, next);
    }

    record Page(List<Event> items, @Nullable Long nextBefore) {}

    record Event(
            long id,
            OffsetDateTime occurredAt,
            String actorType,
            @Nullable UUID actorUserId,
            @Nullable UUID actorTokenId,
            String action,
            String outcome,
            @Nullable String targetType,
            @Nullable String targetId,
            Map<String, Object> details,
            @Nullable String requestId) {}
}
