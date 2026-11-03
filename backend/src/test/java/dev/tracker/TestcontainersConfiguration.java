package dev.tracker;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Real PostgreSQL for tests and local runs — never an in-memory substitute (ADR-0014). */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // Keep in step with the postgres image in compose.yaml.
    public static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:18.6-alpine");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }
}
