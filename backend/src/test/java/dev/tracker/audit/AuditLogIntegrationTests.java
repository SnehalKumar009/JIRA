package dev.tracker.audit;

import static dev.tracker.jooq.Tables.AUDIT_EVENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tracker.TestcontainersConfiguration;
import java.util.Map;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AuditLogIntegrationTests {

    @Autowired
    AuditLog audit;

    @Autowired
    DSLContext dsl;

    @Test
    void recordsAreStoredWithActorTargetAndDetails() {
        audit.record(AuditRecord.success(AuditActor.system(), "test.record")
                .target("thing", "42")
                .details(Map.of("count", 3)));

        var row = dsl.selectFrom(AUDIT_EVENT).where(AUDIT_EVENT.ACTION.eq("test.record")).fetchSingle();
        assertThat(row.getActorType()).isEqualTo("system");
        assertThat(row.getOutcome()).isEqualTo("success");
        assertThat(row.getTargetType()).isEqualTo("thing");
        assertThat(row.getTargetId()).isEqualTo("42");
        assertThat(row.getDetails().data()).contains("\"count\"").contains("3");
    }

    @Test
    void theAuditTableIsAppendOnly() {
        audit.record(AuditRecord.failure(AuditActor.anonymous(), "test.append-only"));

        assertThatThrownBy(() -> dsl.update(AUDIT_EVENT).set(AUDIT_EVENT.OUTCOME, "success")
                .where(AUDIT_EVENT.ACTION.eq("test.append-only")).execute())
                .rootCause().hasMessageContaining("append-only");
        assertThatThrownBy(() -> dsl.deleteFrom(AUDIT_EVENT).where(AUDIT_EVENT.ACTION.eq("test.append-only")).execute())
                .rootCause().hasMessageContaining("append-only");
        assertThatThrownBy(() -> dsl.truncate(AUDIT_EVENT).execute()).rootCause().hasMessageContaining("append-only");
    }
}
