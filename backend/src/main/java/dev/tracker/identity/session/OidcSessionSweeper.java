package dev.tracker.identity.session;

import static dev.tracker.jooq.Tables.OIDC_SESSION;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.selectOne;
import static org.jooq.impl.DSL.table;

import java.time.OffsetDateTime;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Removes back-channel links whose session has expired. Spring Session's JDBC store deletes
 * expired sessions without publishing events, so the registry can't hear about it.
 */
@Component
class OidcSessionSweeper {

    private static final Logger log = LoggerFactory.getLogger(OidcSessionSweeper.class);

    private final DSLContext dsl;

    OidcSessionSweeper(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT1M")
    void sweep() {
        // The grace period covers a session that is linked before its request finishes and saves it.
        int removed = dsl.deleteFrom(OIDC_SESSION)
                .where(OIDC_SESSION.CREATED_AT.lt(OffsetDateTime.now().minusHours(1)))
                .andNotExists(selectOne()
                        .from(table(name("spring_session")))
                        .where(field(name("spring_session", "session_id"), String.class).eq(OIDC_SESSION.SESSION_ID)))
                .execute();
        if (removed > 0) {
            log.info("Removed {} back-channel links to expired sessions", removed);
        }
    }
}
