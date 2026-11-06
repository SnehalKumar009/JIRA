package dev.tracker;

import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Fails the build when one module reaches into another module's internals (ADR-0001, ADR-0014). */
class ModularityTests {

    @Test
    void moduleBoundariesAreRespected() {
        // Generated jOOQ classes are shared plumbing, not a module; table ownership is checked separately.
        ApplicationModules.of(TrackerApplication.class, JavaClass.Predicates.resideInAPackage("dev.tracker.jooq.."))
                .verify();
    }
}
