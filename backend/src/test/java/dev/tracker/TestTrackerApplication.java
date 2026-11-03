package dev.tracker;

import org.springframework.boot.SpringApplication;

/**
 * Runs the app locally against a throwaway PostgreSQL container: {@code ./mvnw spring-boot:test-run}.
 * The full stack (Keycloak, Caddy, persistent data) runs under Docker Compose instead.
 */
public class TestTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.from(TrackerApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
