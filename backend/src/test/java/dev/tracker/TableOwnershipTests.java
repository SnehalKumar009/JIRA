package dev.tracker;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Each table belongs to one module, and only that module may touch it through jOOQ — modules talk
 * through each other's Java APIs, never each other's tables (ADR-0001).
 */
class TableOwnershipTests {

    private static final Map<String, String> OWNER_BY_TABLE = Map.of(
            "AppUser", "identity",
            "AppGroup", "identity",
            "GroupMembership", "identity",
            "PersonalAccessToken", "identity",
            "OidcSession", "identity",
            "AuditEvent", "audit",
            "SystemMetadata", "platform");

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("dev.tracker");

    @Test
    void onlyTheOwningModuleUsesATable() {
        OWNER_BY_TABLE.forEach((table, owner) -> noClasses()
                .that().resideOutsideOfPackages("dev.tracker." + owner + "..", "dev.tracker.jooq..")
                .should().dependOnClassesThat().haveNameMatching(
                        "dev\\.tracker\\.jooq\\.tables\\.(records\\.)?" + table + "(Record)?")
                .because(table + " belongs to the " + owner + " module")
                .check(classes));
    }
}
