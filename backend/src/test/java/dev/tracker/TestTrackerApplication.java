package dev.tracker;

import org.springframework.boot.SpringApplication;

/**
 * Runs the app locally against a throwaway PostgreSQL container: {@code ./mvnw spring-boot:test-run}.
 * The full stack (Keycloak, Caddy, persistent data) runs under Docker Compose instead.
 */
public class TestTrackerApplication {

    public static void main(String[] args) {
        // The test profile supplies placeholder OIDC settings; sign-in needs the full stack.
        SpringApplication.from(TrackerApplication::main)
                .with(TestcontainersConfiguration.class)
                .withAdditionalProfiles("test")
                .run(args);
    }
}
