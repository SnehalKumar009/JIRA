package dev.tracker.platform.system;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads installation-wide key/value metadata written by migrations. */
@Repository
class SystemMetadataRepository {

    private final JdbcClient jdbc;

    SystemMetadataRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<String> find(String key) {
        return jdbc.sql("SELECT value FROM system_metadata WHERE key = :key")
                .param("key", key)
                .query(String.class)
                .optional();
    }
}
