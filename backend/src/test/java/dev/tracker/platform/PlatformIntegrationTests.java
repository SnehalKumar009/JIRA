package dev.tracker.platform;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tracker.TestcontainersConfiguration;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@Import(TestcontainersConfiguration.class)
class PlatformIntegrationTests {

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @LocalManagementPort
    int managementPort;

    @Test
    void systemInfoReturnsInstallationIdCreatedByMigration() throws Exception {
        HttpResponse<String> response = get(port, "/api/v1/system/info", null);

        assertThat(response.statusCode()).isEqualTo(200);
        String body = response.body();
        assertThat(body).contains("\"name\":\"tracker\"");
        String installationId = body.replaceAll(".*\"installationId\":\"([^\"]+)\".*", "$1");
        assertThat(UUID.fromString(installationId)).isNotNull();
    }

    @Test
    void responsesCarrySecurityHeaders() throws Exception {
        HttpResponse<String> response = get(port, "/api/v1/system/info", null);

        assertThat(response.headers().firstValue("Content-Security-Policy"))
                .hasValueSatisfying(csp -> assertThat(csp).contains("default-src 'self'", "frame-ancestors 'none'"));
        assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(response.headers().firstValue("Referrer-Policy")).hasValue("strict-origin-when-cross-origin");
        assertThat(response.headers().firstValue("Permissions-Policy")).isPresent();
    }

    @Test
    void hstsIsSentWhenTheProxyReportsHttps() throws Exception {
        HttpResponse<String> plain = get(port, "/api/v1/system/info", null);
        HttpResponse<String> viaProxy = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/system/info"))
                        .header("X-Forwarded-Proto", "https")
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(plain.headers().firstValue("Strict-Transport-Security")).isEmpty();
        assertThat(viaProxy.headers().firstValue("Strict-Transport-Security"))
                .hasValueSatisfying(hsts -> assertThat(hsts).contains("max-age=31536000", "includeSubDomains"));
    }

    @Test
    void wellFormedRequestIdIsEchoedAndMalformedOneIsReplaced() throws Exception {
        HttpResponse<String> echoed = get(port, "/api/v1/system/info", "abc-123");
        HttpResponse<String> badCharacters = get(port, "/api/v1/system/info", "bad id <script>");
        HttpResponse<String> tooLong = get(port, "/api/v1/system/info", "a".repeat(65));

        assertThat(echoed.headers().firstValue("X-Request-Id")).hasValue("abc-123");
        for (HttpResponse<String> replaced : java.util.List.of(badCharacters, tooLong)) {
            assertThat(replaced.headers().firstValue("X-Request-Id"))
                    .hasValueSatisfying(id -> assertThat(UUID.fromString(id)).isNotNull());
        }
    }

    @Test
    void unknownApiPathReturnsProblemDetailWithRequestId() throws Exception {
        HttpResponse<String> response = get(port, "/api/v1/does-not-exist", "trace-me");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(response.body()).contains("\"status\":404", "\"requestId\":\"trace-me\"");
    }

    @Test
    void openApiSpecIsServed() throws Exception {
        HttpResponse<String> response = get(port, "/api/v1/openapi.json", null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"title\":\"Tracker API\"", "/api/v1/system/info");
    }

    @Test
    void probesAndMetricsAreOnTheManagementPortOnly() throws Exception {
        assertThat(get(managementPort, "/actuator/health/readiness", null).body()).contains("\"status\":\"UP\"");
        assertThat(get(managementPort, "/actuator/health/liveness", null).body()).contains("\"status\":\"UP\"");
        assertThat(get(managementPort, "/actuator/prometheus", null).body()).contains("jvm_memory_used_bytes");

        assertThat(get(port, "/actuator/health", null).statusCode()).isEqualTo(404);
    }

    private HttpResponse<String> get(int targetPort, String path, String requestId)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + targetPort + path));
        if (requestId != null) {
            request.header("X-Request-Id", requestId);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
