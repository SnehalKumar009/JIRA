package dev.tracker.platform.system;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System")
class SystemInfoController {

    private final SystemMetadataRepository metadata;
    private final String version;

    SystemInfoController(SystemMetadataRepository metadata, ObjectProvider<BuildProperties> build) {
        this.metadata = metadata;
        BuildProperties props = build.getIfAvailable();
        this.version = props != null ? props.getVersion() : "dev";
    }

    /**
     * The installation ID is generated once by the first migration, so it changes only if the
     * database is lost — the persistence and backup/restore checks rely on that.
     */
    @GetMapping("/info")
    @Operation(summary = "Application name, version and installation ID")
    SystemInfo info() {
        String installationId = metadata.find("installation_id")
                .orElseThrow(() -> new IllegalStateException("installation_id missing from system_metadata"));
        return new SystemInfo("tracker", version, installationId);
    }

    record SystemInfo(String name, String version, String installationId) {}
}
