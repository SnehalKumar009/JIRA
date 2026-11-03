package dev.tracker;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Fails the build when one module reaches into another module's internals (ADR-0001, ADR-0014). */
class ModularityTests {

    @Test
    void moduleBoundariesAreRespected() {
        ApplicationModules.of(TrackerApplication.class).verify();
    }
}
